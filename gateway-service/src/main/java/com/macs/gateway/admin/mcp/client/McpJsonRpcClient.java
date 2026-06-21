package com.macs.gateway.admin.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.macs.gateway.admin.mcp.service.CallerIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP(Model Context Protocol) JSON-RPC 클라이언트 (reactive).
 *
 * <p>전송 계층: Streamable HTTP — 매 호출마다 endpoint 에 POST.
 * application/json 또는 text/event-stream 응답 양쪽 지원 (첫 data 라인 추출).</p>
 *
 * <p>호출 대상은 MCP 업스트림이 아니라 <b>게이트웨이 자기 라우트(loopback)</b> 의
 * 절대 URL(예: {@code http://localhost:8080/mcp/dummy-mcp}) 이다. 호출자 신원
 * 헤더(Authorization/app_name/employee_number)를 그대로 전달해 라우트의
 * {@code HeaderValidationFilter}/{@code AuthValidation} 필터가 인증·인가하도록 한다.
 * 업스트림 자체 인증(bearer)이 필요하면 라우트 필터로 주입한다(여기서 다루지 않음).</p>
 *
 * <p>세션: <b>stateful</b>. 매 논리 호출(도구 목록/실행)마다 한 세션을 연다 —
 * {@code initialize} 응답의 {@code Mcp-Session-Id} 헤더를 캡처하고,
 * {@code notifications/initialized} 를 같은 세션으로 보낸 뒤, 실제 method(tools/list,
 * tools/call) 를 그 세션 헤더와 함께 호출한다. 서버가 세션을 안 주면(stateless 서버)
 * 세션 헤더 없이 동작 — 하위 호환.</p>
 */
@Component
public class McpJsonRpcClient {

    private static final Logger log = LoggerFactory.getLogger(McpJsonRpcClient.class);

    /** MCP 2025-03-26 spec. dummy server 와 맞춤. */
    public static final String PROTOCOL_VERSION = "2025-03-26";

    /** MCP Streamable HTTP 세션 헤더. initialize 응답에서 받아 이후 요청에 동봉. */
    private static final String SESSION_HEADER = "Mcp-Session-Id";

    /** 단일 JSON-RPC 교환 결과 — result 노드 + (협상된) 세션 id. */
    private record McpRpcResult(JsonNode result, String sessionId) {}

    private final WebClient mcpWebClient;
    private final ObjectMapper objectMapper;
    private final AtomicLong idSeq = new AtomicLong();

    public McpJsonRpcClient(@Qualifier("mcpWebClient") WebClient mcpWebClient,
                            ObjectMapper objectMapper) {
        this.mcpWebClient = mcpWebClient;
        this.objectMapper = objectMapper;
    }

    /** 세션 열기(initialize → notifications/initialized) → tools/list. */
    public Mono<JsonNode> listTools(String mcpId, String targetUrl, CallerIdentity caller) {
        return openSession(mcpId, targetUrl, caller)
                .flatMap(sid -> call(mcpId, targetUrl, caller, sid, "tools/list", null));
    }

    public Mono<JsonNode> callTool(String mcpId, String targetUrl, CallerIdentity caller,
                                   String toolName, JsonNode arguments) {
        return openSession(mcpId, targetUrl, caller).flatMap(sid -> {
            ObjectNode params = objectMapper.createObjectNode();
            params.put("name", toolName);
            if (arguments != null && !arguments.isNull()) {
                params.set("arguments", arguments);
            } else {
                params.set("arguments", objectMapper.createObjectNode());
            }
            return call(mcpId, targetUrl, caller, sid, "tools/call", params);
        });
    }

    /**
     * MCP 핸드셰이크: {@code initialize} 로 세션을 협상하고({@code Mcp-Session-Id} 캡처),
     * {@code notifications/initialized} 를 같은 세션으로 보낸다.
     * 반환값은 세션 id ("" = 서버가 세션을 안 줌). 연결성 점검(health)에도 사용.
     */
    public Mono<String> openSession(String mcpId, String targetUrl, CallerIdentity caller) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.set("capabilities", objectMapper.createObjectNode());
        ObjectNode clientInfo = objectMapper.createObjectNode();
        clientInfo.put("name", "macs-gateway-service");
        clientInfo.put("version", "1.0.0");
        params.set("clientInfo", clientInfo);

        return exchange(mcpId, targetUrl, caller, null, "initialize", params, false)
                .flatMap(init -> {
                    String sid = init.sessionId();
                    if (sid != null && !sid.isBlank()) {
                        log.debug("MCP session opened id={} session={}", mcpId, sid);
                    }
                    // spec: initialize 결과 수신 후 같은 세션으로 initialized 통지 (notification: id 없음)
                    return exchange(mcpId, targetUrl, caller, sid, "notifications/initialized", null, true)
                            .thenReturn(sid == null ? "" : sid);
                });
    }

    /** 세션 헤더를 동봉해 일반 method 호출, result 노드만 반환. */
    private Mono<JsonNode> call(String mcpId, String targetUrl, CallerIdentity caller,
                                String sessionId, String method, JsonNode params) {
        return exchange(mcpId, targetUrl, caller, sessionId, method, params, false)
                .map(McpRpcResult::result);
    }

    /**
     * JSON-RPC 교환 1회. 요청에 세션 헤더를 동봉하고, 응답의 {@code Mcp-Session-Id} 를 캡처해
     * {@link McpRpcResult} 로 돌려준다. {@code notification=true} 면 id 없이 보내고 본문을 파싱하지 않는다.
     */
    private Mono<McpRpcResult> exchange(String mcpId, String targetUrl, CallerIdentity caller,
                                        String sessionId, String method, JsonNode params,
                                        boolean notification) {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        if (!notification) {
            req.put("id", idSeq.incrementAndGet());
        }
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
        log.debug("MCP rpc → id={} url={} method={} session={} body={}",
                mcpId, targetUrl, method, sessionId, body);

        return mcpWebClient.post()
                .uri(targetUrl)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .headers(h -> {
                    h.setContentType(MediaType.APPLICATION_JSON);
                    // 호출자 신원 전달 — 라우트의 HeaderValidation/AuthValidation 필터가 사용.
                    if (caller != null) {
                        if (caller.authorization() != null) {
                            h.set(HttpHeaders.AUTHORIZATION, caller.authorization());
                        }
                        if (caller.appName() != null) {
                            h.set("app_name", caller.appName());
                        }
                        if (caller.employeeNumber() != null) {
                            h.set("employee_number", caller.employeeNumber());
                        }
                    }
                    // stateful 서버: initialize 에서 받은 세션을 이후 요청에 동봉.
                    if (sessionId != null && !sessionId.isBlank()) {
                        h.set(SESSION_HEADER, sessionId);
                    }
                })
                .bodyValue(body)
                .retrieve()
                .toEntity(String.class)
                .flatMap(entity -> {
                    String respSid = entity.getHeaders().getFirst(SESSION_HEADER);
                    String sid = (respSid != null && !respSid.isBlank()) ? respSid : sessionId;
                    // notification(예: notifications/initialized) 은 202 + 빈 본문 — 파싱 생략.
                    if (notification) {
                        return Mono.just(new McpRpcResult(null, sid));
                    }
                    String responseBody = entity.getBody();
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
                            log.warn("MCP rpc error id={} method={} code={} msg={}",
                                    mcpId, method, code, msg);
                            return Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                    "MCP error [" + code + "] " + msg));
                        }
                        JsonNode result = resp.get("result");
                        if (result == null) {
                            return Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                    "MCP response missing 'result' field for method=" + method));
                        }
                        log.debug("MCP rpc ← id={} method={} session={} result.size={}",
                                mcpId, method, sid, result.size());
                        return Mono.just(new McpRpcResult(result, sid));
                    } catch (Exception ex) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                "MCP response parse error: " + ex.getMessage()));
                    }
                })
                .onErrorResume(ResponseStatusException.class, Mono::error)
                // 라우트(loopback)가 낸 4xx/5xx 는 상태코드를 그대로 전파한다.
                // (예: AuthValidation 403 → 502 로 뭉개지 않도록 generic 핸들러보다 먼저.)
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.warn("MCP rpc downstream status id={} url={} method={} status={}",
                            mcpId, targetUrl, method, ex.getStatusCode());
                    return Mono.error(new ResponseStatusException(ex.getStatusCode(),
                            "MCP route returned " + ex.getStatusCode()));
                })
                .onErrorResume(WebClientException.class, ex -> {
                    log.error("MCP rpc transport failure id={} url={} method={} type={} msg={}",
                            mcpId, targetUrl, method, ex.getClass().getSimpleName(), ex.getMessage());
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
