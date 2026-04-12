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

import java.util.List;
import java.util.Map;

/**
 * Per-route authentication filter. Apply via:
 * <pre>
 *   filters:
 *     - name: AuthValidation
 *       args:
 *         connector: portal      # logical connector/app name
 * </pre>
 * Shorthand form (<code>- AuthValidation=portal</code>) is also supported.
 *
 * <p>If {@code connector} is omitted, the filter falls back to the route id.
 *
 * <p>The filter calls {@code POST /api/auth/validate} on auth-server with
 * {@code {connector: ...}}. Auth-server verifies the JWT and checks whether
 * its embedded {@code permissions} claim contains an entry whose
 * {@code connector} equals this value.
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

            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            String routeId = route != null ? route.getId() : "";
            String targetConnector = (config.getConnector() != null && !config.getConnector().isBlank())
                    ? config.getConnector()
                    : routeId;

            return authServiceWebClient.post()
                    .uri("/api/auth/validate")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .bodyValue(Map.of("connector", targetConnector))
                    .retrieve()
                    .toBodilessEntity()
                    .then(chain.filter(exchange))
                    .onErrorResume(ex -> {
                        log.warn("Auth validation failed for connector={} route={}: {}",
                                targetConnector, routeId, ex.getMessage());
                        return HeaderValidationFilter.writeError(
                                exchange, HttpStatus.FORBIDDEN,
                                "Access denied to " + targetConnector);
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
