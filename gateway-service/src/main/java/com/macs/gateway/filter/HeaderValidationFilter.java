package com.macs.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class HeaderValidationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(HeaderValidationFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (shouldSkip(exchange)) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();
        String appName = exchange.getRequest().getHeaders().getFirst("app_name");
        String employeeNumber = exchange.getRequest().getHeaders().getFirst("employee_number");

        if (appName == null || appName.isBlank()) {
            log.warn("Reject 400: missing app_name header path={}", path);
            return writeError(exchange, HttpStatus.BAD_REQUEST, "Missing required header: app_name");
        }
        if (employeeNumber == null || employeeNumber.isBlank()) {
            log.warn("Reject 400: missing employee_number header path={} app={}", path, appName);
            return writeError(exchange, HttpStatus.BAD_REQUEST, "Missing required header: employee_number");
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private boolean shouldSkip(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        return path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/webjars");
    }

    static Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"error":"%s","message":"%s"}""".formatted(status.getReasonPhrase(), message);
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
