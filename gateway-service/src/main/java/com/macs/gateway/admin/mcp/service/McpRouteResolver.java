package com.macs.gateway.admin.mcp.service;

import com.macs.gateway.admin.property.dto.GatewayDefinition;
import com.macs.gateway.admin.property.dto.RouteResponse;
import com.macs.gateway.admin.property.service.ConfigPropertyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * MCP 서버 id → 게이트웨이 라우트 정보 resolver.
 *
 * <p>MCP 호출은 더 이상 업스트림 endpoint 로 직접 가지 않고, 같은 id 로 등록된
 * 게이트웨이 라우트({@code Path=/mcp/{id}})를 <b>loopback</b> 으로 통과한다.
 * 그래야 라우트에 걸린 필터(AuthValidation, RateLimiter 등)가 적용된다.
 * 업스트림 주소의 단일 소스는 라우트의 {@code uri} 이다.
 */
@Component
public class McpRouteResolver {

    private static final Logger log = LoggerFactory.getLogger(McpRouteResolver.class);

    private static final String GATEWAY_APP = "gateway-service";
    private static final String GATEWAY_PROFILE = "default";
    private static final String GATEWAY_LABEL = "main";

    private final ConfigPropertyService configPropertyService;
    private final String loopbackBaseUrl;

    public McpRouteResolver(ConfigPropertyService configPropertyService,
                            @Value("${macs.gateway.loopback-base-url:http://localhost:8080}") String loopbackBaseUrl) {
        this.configPropertyService = configPropertyService;
        this.loopbackBaseUrl = stripTrailingSlash(loopbackBaseUrl);
    }

    /** 전체 라우트를 id → RouteResponse 로. list 화면에서 서버별 라우트 join 용. */
    public Mono<Map<String, RouteResponse>> routesById() {
        return configPropertyService.findRoutes(GATEWAY_APP, GATEWAY_PROFILE, GATEWAY_LABEL)
                .map(routes -> routes.stream()
                        .filter(r -> r.id() != null)
                        .collect(Collectors.toMap(RouteResponse::id, Function.identity(), (a, b) -> a)));
    }

    /** RouteResponse 에서 Path predicate 추출. 없으면 null. */
    public String pathOf(RouteResponse route) {
        return route == null ? null : extractPathPredicate(route.predicates());
    }

    /** id 에 매칭되는 라우트가 있으면 해당 RouteResponse, 없으면 empty. */
    public Mono<RouteResponse> findRoute(String mcpId) {
        return configPropertyService.findRoutes(GATEWAY_APP, GATEWAY_PROFILE, GATEWAY_LABEL)
                .flatMap(routes -> Mono.justOrEmpty(routes.stream()
                        .filter(r -> mcpId.equals(r.id()))
                        .findFirst()));
    }

    public Mono<Boolean> routeExists(String mcpId) {
        return findRoute(mcpId).hasElement();
    }

    /** 라우트의 업스트림 uri (endpointUrl denormalize 용). 없으면 BAD_REQUEST. */
    public Mono<String> resolveUpstreamUri(String mcpId) {
        return findRoute(mcpId)
                .switchIfEmpty(noRoute(mcpId, HttpStatus.BAD_REQUEST))
                .map(RouteResponse::uri);
    }

    /** 라우트의 Path predicate (예: /mcp/dummy-mcp). 없으면 BAD_GATEWAY. */
    public Mono<String> resolveGatewayPath(String mcpId) {
        return findRoute(mcpId)
                .switchIfEmpty(noRoute(mcpId, HttpStatus.BAD_GATEWAY))
                .flatMap(route -> {
                    String path = extractPathPredicate(route.predicates());
                    if (path == null || path.isBlank()) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                "MCP route has no Path predicate: " + mcpId));
                    }
                    return Mono.just(path);
                });
    }

    /** loopback 게이트웨이 호출용 절대 URL (예: http://localhost:8080/mcp/dummy-mcp). */
    public Mono<String> resolveLoopbackUrl(String mcpId) {
        return resolveGatewayPath(mcpId).map(path -> loopbackBaseUrl + ensureLeadingSlash(path));
    }

    private <T> Mono<T> noRoute(String mcpId, HttpStatus status) {
        return Mono.error(() -> {
            log.warn("No gateway route for MCP id={} (status={})", mcpId, status);
            return new ResponseStatusException(status, "No matching gateway route for MCP id: " + mcpId);
        });
    }

    static String extractPathPredicate(List<GatewayDefinition> predicates) {
        if (predicates == null) return null;
        return predicates.stream()
                .filter(p -> "Path".equalsIgnoreCase(p.name()))
                .map(p -> firstArg(p.args()))
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static String firstArg(Map<String, String> args) {
        return Optional.ofNullable(args)
                .filter(a -> !a.isEmpty())
                .map(a -> a.values().iterator().next())
                .orElse(null);
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String ensureLeadingSlash(String s) {
        return s.startsWith("/") ? s : "/" + s;
    }
}
