package com.macs.gateway.filter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-route authentication filter. Apply via:
 * <pre>
 *   filters:
 *     - name: AuthValidation
 *       args:
 *         connector: portal
 * </pre>
 * Shorthand: <code>- AuthValidation=portal</code>.
 *
 * <p>If {@code connector} is omitted, the filter falls back to the route id.
 *
 * <p>The filter delegates verification to auth-server via
 * {@code POST /api/auth/validate} with body {@code {client_app, employee_number, connector}}.
 * Auth-server verifies the JWT signature, asserts the body values match the token claims,
 * looks up PERMISSION for {@code (client_app, employee_number, connector)}, and returns
 * {@code {valid, allowed, employee_number}}. Gateway returns 403 when {@code allowed=false}
 * and 401 on signature/expiry failures.
 */
@Component
public class AuthValidationGatewayFilterFactory
        extends AbstractGatewayFilterFactory<AuthValidationGatewayFilterFactory.Config> {

    private static final Logger log = LogManager.getLogger(AuthValidationGatewayFilterFactory.class);

    private final WebClient authServiceWebClient;

    public AuthValidationGatewayFilterFactory(WebClient authServiceWebClient) {
        super(Config.class);
        this.authServiceWebClient = authServiceWebClient;
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("connector");
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String authorization = exchange.getRequest()
                    .getHeaders()
                    .getFirst(HttpHeaders.AUTHORIZATION);
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                return HeaderValidationFilter.writeError(
                        exchange, HttpStatus.UNAUTHORIZED,
                        "Missing or invalid Authorization header");
            }

            String clientApp = exchange.getRequest().getHeaders().getFirst("Client-App");
            if (clientApp == null || clientApp.isBlank()) {
                // HeaderValidationFilter normally catches this first; this is a safety net.
                return HeaderValidationFilter.writeError(
                        exchange, HttpStatus.BAD_REQUEST,
                        "Missing required header: Client-App");
            }
            String employeeNumber = exchange.getRequest().getHeaders().getFirst("Employee-Number");
            if (employeeNumber == null || employeeNumber.isBlank()) {
                return HeaderValidationFilter.writeError(
                        exchange, HttpStatus.BAD_REQUEST,
                        "Missing required header: Employee-Number");
            }

            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            String routeId = route != null ? route.getId() : "";
            String targetConnector = (config.getConnector() != null && !config.getConnector().isBlank())
                    ? config.getConnector()
                    : routeId;

            Map<String, String> body = new HashMap<>();
            body.put("client_app", clientApp);
            body.put("employee_number", employeeNumber);
            body.put("connector", targetConnector);

            return authServiceWebClient.post()
                    .uri("/api/auth/validate")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .flatMap(resp -> {
                        Object allowed = resp.get("allowed");
                        if (Boolean.TRUE.equals(allowed)) {
                            return chain.filter(exchange);
                        }
                        log.warn("Auth denied: client_app={} employee_number={} connector={} route={}",
                                clientApp, employeeNumber, targetConnector, routeId);
                        return HeaderValidationFilter.writeError(
                                exchange, HttpStatus.FORBIDDEN,
                                "Access denied to " + targetConnector);
                    })
                    .onErrorResume(ex -> {
                        log.warn("Auth validation error client_app={} employee_number={} connector={} route={}: {}",
                                clientApp, employeeNumber, targetConnector, routeId, ex.getMessage());
                        return HeaderValidationFilter.writeError(
                                exchange, HttpStatus.UNAUTHORIZED,
                                "Token validation failed");
                    });
        };
    }

    public static class Config {
        private String connector;

        public String getConnector() {
            return connector;
        }

        public void setConnector(String connector) {
            this.connector = connector;
        }
    }
}
