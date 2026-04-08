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
            "/api/qa/**",
            "/actuator/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/webjars/**"
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

        return authServiceWebClient.post()
                .uri("/api/auth/validate")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .bodyValue(Map.of("request_app", path))
                .retrieve()
                .toBodilessEntity()
                .then(chain.filter(exchange))
                .onErrorResume(ex -> {
                    log.warn("Auth validation failed for path={}: {}", path, ex.getMessage());
                    return HeaderValidationFilter.writeError(
                            exchange, HttpStatus.FORBIDDEN, "Access denied");
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    private boolean shouldSkip(String path) {
        return SKIP_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
}
