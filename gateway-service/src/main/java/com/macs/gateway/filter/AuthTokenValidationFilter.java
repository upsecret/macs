package com.macs.gateway.filter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
public class AuthTokenValidationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LogManager.getLogger(AuthTokenValidationFilter.class);

    private static final List<String> SKIP_PATHS = List.of(
            "/api/auth/**",
            "/api/config/**",
            "/actuator/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/webjars/**"
    );

    /**
     * URL 경로 → 커넥터 이름 매핑.
     * auth-server의 allowed_resources_list에는 커넥터 이름이 들어있으므로
     * 요청 경로를 커넥터 이름으로 변환하여 validate에 전달한다.
     */
    private static final Map<String, String> PATH_TO_CONNECTOR = Map.of(
            "/api/qa/**", "qa-tool",
            "/portal/**", "portal"
    );

    private final WebClient authServiceWebClient;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AuthTokenValidationFilter(WebClient authServiceWebClient) {
        this.authServiceWebClient = authServiceWebClient;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (shouldSkip(path)) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return HeaderValidationFilter.writeError(
                    exchange, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }

        // URL → 커넥터 이름 변환 (매핑 없으면 경로 그대로 전달)
        String connectorName = resolveConnector(path);

        return authServiceWebClient.post()
                .uri("/api/auth/validate")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .bodyValue(Map.of("request_app", connectorName))
                .retrieve()
                .toBodilessEntity()
                .then(chain.filter(exchange))
                .onErrorResume(ex -> {
                    log.warn("Auth validation failed for path={} connector={}: {}",
                            path, connectorName, ex.getMessage());
                    return HeaderValidationFilter.writeError(
                            exchange, HttpStatus.FORBIDDEN, "Access denied to " + connectorName);
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    private boolean shouldSkip(String path) {
        return SKIP_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private String resolveConnector(String path) {
        for (var entry : PATH_TO_CONNECTOR.entrySet()) {
            if (pathMatcher.match(entry.getKey(), path)) {
                return entry.getValue();
            }
        }
        return path;
    }
}
