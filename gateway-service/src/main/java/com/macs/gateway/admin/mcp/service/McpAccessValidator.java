package com.macs.gateway.admin.mcp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP 호출에 대한 권한 게이트.
 *
 * <p>MCP 컨트롤러는 게이트웨이 라우트가 아니라 로컬 {@code @RestController} 라서
 * 게이트웨이의 {@code HeaderValidationFilter}/{@code AuthValidation} 필터를 타지 않는다.
 * 따라서 API 커넥터와 동일한 보안 모델을 적용하기 위해, 여기서 직접
 * auth-server {@code POST /api/auth/validate} 를 호출한다.
 *
 * <p>논리 커넥터 id 규칙: {@code mcp:{serverId}} — PERMISSION 테이블에 이 connector 로
 * 권한을 부여하면 해당 사번/client_app 이 그 MCP 서버를 사용할 수 있다.
 */
@Component
public class McpAccessValidator {

    private static final Logger log = LoggerFactory.getLogger(McpAccessValidator.class);

    /** PERMISSION.connector 규칙: mcp:{serverId} */
    public static final String CONNECTOR_PREFIX = "mcp:";

    private final WebClient authServiceWebClient;

    public McpAccessValidator(WebClient authServiceWebClient) {
        this.authServiceWebClient = authServiceWebClient;
    }

    /**
     * 헤더(app_name, employee_number) + Bearer 토큰을 검증하고,
     * {@code connector=mcp:{serverId}} 권한을 확인한다.
     * 통과하면 {@code Mono<Void>} 완료, 아니면 401/403/400 에러.
     */
    public Mono<Void> authorize(ServerWebExchange exchange, String serverId) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
        String appName = headers.getFirst("app_name");
        String employeeNumber = headers.getFirst("employee_number");

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header"));
        }
        if (appName == null || appName.isBlank()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Missing required header: app_name"));
        }
        if (employeeNumber == null || employeeNumber.isBlank()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Missing required header: employee_number"));
        }

        String connector = CONNECTOR_PREFIX + serverId;
        Map<String, String> body = new HashMap<>();
        body.put("app_name", appName);
        body.put("employee_number", employeeNumber);
        body.put("connector", connector);

        return authServiceWebClient.post()
                .uri("/api/auth/validate")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(resp -> {
                    if (Boolean.TRUE.equals(resp.get("allowed"))) {
                        log.info("MCP access ALLOW app={} emp={} connector={}", appName, employeeNumber, connector);
                        return Mono.empty();
                    }
                    log.warn("MCP access DENY app={} emp={} connector={}", appName, employeeNumber, connector);
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.FORBIDDEN, "Access denied to " + connector));
                })
                .onErrorMap(ex -> !(ex instanceof ResponseStatusException), ex -> {
                    log.warn("MCP access validation error connector={}: {}", connector, ex.getMessage());
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token validation failed");
                })
                .then();
    }
}
