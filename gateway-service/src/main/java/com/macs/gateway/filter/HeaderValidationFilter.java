package com.macs.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class HeaderValidationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(HeaderValidationFilter.class);

    /**
     * 헤더 검증을 건너뛸 gateway route id 목록.
     * 포털 SPA 는 HTML/정적 리소스라 Client-App/Employee-Number 헤더가 없음.
     * 이외 모든 라우트(auth, admin, rms, fdc, token-dic 등)는 검증 대상.
     */
    private static final Set<String> SKIPPED_ROUTE_IDS = Set.of("portal-route");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (shouldSkip(exchange)) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();
        String clientApp = exchange.getRequest().getHeaders().getFirst("Client-App");
        String employeeNumber = exchange.getRequest().getHeaders().getFirst("Employee-Number");

        if (clientApp == null || clientApp.isBlank()) {
            log.warn("Reject 400: missing Client-App header path={}", path);
            return writeError(exchange, HttpStatus.BAD_REQUEST, "Missing required header: Client-App");
        }
        if (employeeNumber == null || employeeNumber.isBlank()) {
            log.warn("Reject 400: missing Employee-Number header path={} client_app={}", path, clientApp);
            return writeError(exchange, HttpStatus.BAD_REQUEST, "Missing required header: Employee-Number");
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private boolean shouldSkip(ServerWebExchange exchange) {
        // 매칭된 gateway route 가 포털이면 skip. 어떤 gateway route 에도 매칭되지
        // 않은 요청(/actuator, /swagger-ui 등 Spring Boot 내부 핸들러가 처리)은
        // 그 자체로 이 필터 체인을 타지 않음 → 별도 처리 불필요.
        Route route = (Route) exchange.getAttributes().get(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route != null && SKIPPED_ROUTE_IDS.contains(route.getId());
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
