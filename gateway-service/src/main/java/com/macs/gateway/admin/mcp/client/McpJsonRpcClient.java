package com.macs.gateway.admin.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.macs.gateway.admin.mcp.domain.McpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP(Model Context Protocol) JSON-RPC 클라이언트 (reactive).
 *
 * <p>전송 계층: Streamable HTTP — 매 호출마다 endpoint 에 POST.
 * application/json 또는 text/event-stream 응답 양쪽 지원 (첫 data 라인 추출).</p>
 *
 * <p>세션: stateless — Phase 1 단순화. 매 호출마다 initialize → 실제 method.</p>
 */
@Component
public class McpJsonRpcClient {

    private static final Logger log = LoggerFactory.getLogger(McpJsonRpcClient.class);

    /** MCP 2025-03-26 spec. dummy server 와 맞춤. */
    public static final String PROTOCOL_VERSION = "2025-03-26";

    private final WebClient mcpWebClient;
    private final ObjectMapper objectMapper;
    private final AtomicLong idSeq = new AtomicLong();

    public McpJsonRpcClient(@Qualifier("mcpWebClient") WebClient mcpWebClient,
                            ObjectMapper objectMapper) {
        this.mcpWebClient = mcpWebClient;
        this.objectMapper = objectMapper;
    }

    /** initialize → tools/list. 결과의 .get("tools") 가 array 노드. */
    public Mono<JsonNode> listTools(McpServer server) {
        return initialize(server).then(rpc(server, "tools/list", null));
    }

    public Mono<JsonNode> callTool(McpServer server, String toolName, JsonNode arguments) {
        return initialize(server).then(Mono.defer(() -> {
            ObjectNode params = objectMapper.createObjectNode();
            params.put("name", toolName);
            if (arguments != null && !arguments.isNull()) {
                params.set("arguments", arguments);
            } else {
                params.set("arguments", objectMapper.createObjectNode());
            }
            return rpc(server, "tools/call", params);
        }));
    }

    public Mono<JsonNode> initialize(McpServer server) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.set("capabilities", objectMapper.createObjectNode());
        ObjectNode clientInfo = objectMapper.createObjectNode();
        clientInfo.put("name", "macs-gateway-service");
        clientInfo.put("version", "1.0.0");
        params.set("clientInfo", clientInfo);
        return rpc(server, "initialize", params);
    }

    /** JSON-RPC 호출 1회. 성공시 result 노드. 에러시 ResponseStatusException(BAD_GATEWAY). */
    public Mono<JsonNode> rpc(McpServer server, String method, JsonNode params) {
        long id = idSeq.incrementAndGet();
        ObjectNode req = objectMapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", id);
        req.put("method", method);
        if (params != null) {
            req.set("params", params);
        }

        String body;
        try {
            body = objectMapper.writeValueAsString(req);
        } catch (Exception ex) {
            return Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "MCP request serialization failed: " + ex.getMessage()));
        }
        log.debug("MCP rpc → server={} method={} body={}", server.getId(), method, body);

        return mcpWebClient.post()
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
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(responseBody -> {
                    if (responseBody == null || responseBody.isBlank()) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                "MCP server returned empty body for method=" + method));
                    }
                    try {
                        String jsonPayload = extractJsonPayload(responseBody);
                        JsonNode resp = objectMapper.readTree(jsonPayload);
                        if (resp.has("error")) {
                            JsonNode error = resp.get("error");
                            int code = error.path("code").asInt(-32000);
                            String msg = error.path("message").asText("MCP error");
                            log.warn("MCP rpc error server={} method={} code={} msg={}",
                                    server.getId(), method, code, msg);
                            return Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                    "MCP error [" + code + "] " + msg));
                        }
                        JsonNode result = resp.get("result");
                        if (result == null) {
                            return Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                    "MCP response missing 'result' field for method=" + method));
                        }
                        log.debug("MCP rpc ← server={} method={} result.size={}",
                                server.getId(), method, result.size());
                        return Mono.just(result);
                    } catch (Exception ex) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                "MCP response parse error: " + ex.getMessage()));
                    }
                })
                .onErrorResume(ResponseStatusException.class, Mono::error)
                .onErrorResume(WebClientException.class, ex -> {
                    log.error("MCP rpc transport failure server={} url={} method={} type={} msg={}",
                            server.getId(), server.getEndpointUrl(), method,
                            ex.getClass().getSimpleName(), ex.getMessage());
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "MCP transport error: " + ex.getMessage()));
                });
    }

    /** application/json 이면 그대로, text/event-stream 이면 첫 data 라인의 JSON 만 추출. */
    static String extractJsonPayload(String body) {
        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }
        for (String line : trimmed.split("\\R")) {
            String s = line.trim();
            if (s.startsWith("data:")) {
                return s.substring("data:".length()).trim();
            }
        }
        return trimmed;
    }
}
