package com.macs.adminserver.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.macs.adminserver.mcp.domain.McpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP(Model Context Protocol) JSON-RPC 클라이언트.
 *
 * <p>전송 계층: Streamable HTTP (단순화) — 매 호출마다 endpoint 에 POST 하고
 * application/json 응답을 받는다. SSE 스트리밍은 Phase 4 로 미룸.</p>
 *
 * <p>세션: 표준은 Mcp-Session-Id 헤더로 세션을 식별하지만, Phase 1 단순화로
 * 매 호출이 stateless — 더미 서버 및 가벼운 외부 서버 호환을 우선시.</p>
 */
@Component
public class McpJsonRpcClient {

    private static final Logger log = LoggerFactory.getLogger(McpJsonRpcClient.class);

    /** MCP 2025-03-26 spec. dummy server 와 맞춤. */
    public static final String PROTOCOL_VERSION = "2025-03-26";

    private final RestClient mcpClient;
    private final ObjectMapper objectMapper;
    private final AtomicLong idSeq = new AtomicLong();

    public McpJsonRpcClient(RestClient mcpClient, ObjectMapper objectMapper) {
        this.mcpClient = mcpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * initialize → tools/list 까지 한 번에 수행하고 도구 목록 JsonNode 를 리턴.
     * 결과의 .get("tools") 가 array 노드.
     */
    public JsonNode listTools(McpServer server) {
        initialize(server);   // 매 호출마다 초기화 (stateless). 캐시는 service 레벨에서.
        return rpc(server, "tools/list", null);
    }

    public JsonNode callTool(McpServer server, String toolName, JsonNode arguments) {
        initialize(server);
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", toolName);
        if (arguments != null && !arguments.isNull()) {
            params.set("arguments", arguments);
        } else {
            params.set("arguments", objectMapper.createObjectNode());
        }
        return rpc(server, "tools/call", params);
    }

    public JsonNode initialize(McpServer server) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.set("capabilities", objectMapper.createObjectNode());
        ObjectNode clientInfo = objectMapper.createObjectNode();
        clientInfo.put("name", "macs-admin-server");
        clientInfo.put("version", "1.0.0");
        params.set("clientInfo", clientInfo);
        return rpc(server, "initialize", params);
    }

    /**
     * JSON-RPC 호출 1회. 성공 시 result 노드를 리턴.
     * 에러면 ResponseStatusException(BAD_GATEWAY) 로 변환.
     */
    public JsonNode rpc(McpServer server, String method, JsonNode params) {
        long id = idSeq.incrementAndGet();
        ObjectNode req = objectMapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", id);
        req.put("method", method);
        if (params != null) {
            req.set("params", params);
        }

        try {
            String body = objectMapper.writeValueAsString(req);
            log.debug("MCP rpc → server={} method={} body={}", server.getId(), method, body);

            String responseBody = mcpClient.post()
                    .uri(server.getEndpointUrl())
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                    .headers(h -> {
                        h.setContentType(MediaType.APPLICATION_JSON);
                        if ("bearer".equalsIgnoreCase(server.getAuthType())
                                && server.getAuthToken() != null
                                && !server.getAuthToken().isBlank()) {
                            h.setBearerAuth(server.getAuthToken());
                        }
                    })
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "MCP server returned empty body for method=" + method);
            }

            // SSE 응답인 경우: "data: {json}\n\n" 형태. 첫 data 라인의 JSON 만 추출.
            String jsonPayload = extractJsonPayload(responseBody);
            JsonNode resp = objectMapper.readTree(jsonPayload);

            if (resp.has("error")) {
                JsonNode error = resp.get("error");
                int code = error.path("code").asInt(-32000);
                String msg = error.path("message").asText("MCP error");
                log.warn("MCP rpc error server={} method={} code={} msg={}",
                        server.getId(), method, code, msg);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "MCP error [" + code + "] " + msg);
            }
            JsonNode result = resp.get("result");
            if (result == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "MCP response missing 'result' field for method=" + method);
            }
            log.debug("MCP rpc ← server={} method={} result.size={}",
                    server.getId(), method, result.size());
            return result;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.error("MCP rpc transport failure server={} url={} method={} type={} msg={}",
                    server.getId(), server.getEndpointUrl(), method,
                    ex.getClass().getSimpleName(), ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "MCP transport error: " + ex.getMessage());
        } catch (Exception ex) {
            log.error("MCP rpc unexpected error server={} method={} type={} msg={}",
                    server.getId(), method, ex.getClass().getSimpleName(), ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "MCP unexpected error: " + ex.getMessage());
        }
    }

    /**
     * application/json 이면 그대로, text/event-stream 이면 첫 data 라인의 JSON 만 추출.
     * Content-Type 헤더를 못 보는 RestClient.body(String) 경로라 본문 형태로 추정.
     */
    private static String extractJsonPayload(String body) {
        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }
        // SSE: 여러 라인 중 첫 "data: " 라인의 JSON 만 사용.
        for (String line : trimmed.split("\\R")) {
            String s = line.trim();
            if (s.startsWith("data:")) {
                return s.substring("data:".length()).trim();
            }
        }
        return trimmed;
    }
}
