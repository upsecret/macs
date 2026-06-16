package com.macs.gateway.filter;

import com.macs.gateway.admin.permission.service.PermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * admin/config 관리 엔드포인트 보호 (앱 레벨 인증·인가).
 *
 * <p>{@code /api/admin/**}, {@code /api/config/**} 는 게이트웨이 라우트가 아니라
 * 로컬 {@code @RestController} 라서 게이트웨이 GlobalFilter 를 타지 않는다. 이 WebFilter 가
 * 그 공백을 메운다. 기본 정책: <b>유효 토큰 + admin role</b>.
 *
 * <p>예외 경로:
 * <ul>
 *   <li>{@code GET /api/admin/permissions/users/{app}/{emp}} — auth-server S2S 역호출(루프 방지)
 *       또는 사용자 본인 권한 조회(로그인). 내부 시크릿 헤더 또는 본인 토큰이면 허용(admin 불필요).</li>
 *   <li>MCP 도구 경로(tools, tools/call) — McpController 가 {@code mcp:{id}}
 *       커넥터 권한으로 자체 게이팅하므로 여기서는 통과.</li>
 * </ul>
 */
@Component
public class AdminAccessFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AdminAccessFilter.class);

    private static final String USER_PERM_PREFIX = "/api/admin/permissions/users/";
    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final WebClient authServiceWebClient;
    private final PermissionService permissionService;
    private final String internalSecret;

    public AdminAccessFilter(WebClient authServiceWebClient,
                             PermissionService permissionService,
                             @Value("${macs.internal.secret:macs-internal-secret-dev}") String internalSecret) {
        this.authServiceWebClient = authServiceWebClient;
        this.permissionService = permissionService;
        this.internalSecret = internalSecret;
    }

    @Override
    public int getOrder() {
        return -50;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!isGated(path)) {
            return chain.filter(exchange);
        }
        // CORS preflight 통과
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }
        // MCP 도구 호출은 McpController 가 mcp:{id} 권한으로 자체 게이팅
        if (isMcpToolPath(path)) {
            return chain.filter(exchange);
        }
        // 사용자 본인 권한 조회 / auth-server S2S 역호출
        if (isUserPermFetch(exchange, path)) {
            return handleUserPermFetch(exchange, chain, path);
        }
        // 그 외 관리 엔드포인트 → 토큰 + admin role
        return requireAdmin(exchange, chain);
    }

    private boolean isGated(String path) {
        return path.startsWith("/api/admin") || path.startsWith("/api/config");
    }

    private boolean isMcpToolPath(String path) {
        return path.matches("/api/admin/mcp/servers/[^/]+/tools(/call)?");
    }

    private boolean isUserPermFetch(ServerWebExchange exchange, String path) {
        return HttpMethod.GET.equals(exchange.getRequest().getMethod())
                && path.startsWith(USER_PERM_PREFIX);
    }

    // ── 사용자 본인 권한 조회 / S2S ───────────────────────────────
    private Mono<Void> handleUserPermFetch(ServerWebExchange exchange, WebFilterChain chain, String path) {
        String secret = exchange.getRequest().getHeaders().getFirst(INTERNAL_SECRET_HEADER);
        if (secret != null && !secret.isBlank()) {
            if (secret.equals(internalSecret)) {
                return chain.filter(exchange);
            }
            log.warn("Admin gate DENY: bad internal secret on {}", path);
            return HeaderValidationFilter.writeError(exchange, HttpStatus.UNAUTHORIZED, "Invalid internal secret");
        }

        String[] seg = path.split("/");
        // ["", api, admin, permissions, users, {app}, {emp}]
        if (seg.length < 7) {
            return requireAdmin(exchange, chain);
        }
        String pathApp = seg[5];
        String pathEmp = seg[6];

        HttpHeaders headers = exchange.getRequest().getHeaders();
        String app = headers.getFirst("app_name");
        String emp = headers.getFirst("employee_number");
        Mono<Void> precheck = checkHeaders(exchange, app, emp);
        if (precheck != null) {
            return precheck;
        }
        // 본인 것만 조회 가능
        if (!app.equals(pathApp) || !emp.equals(pathEmp)) {
            log.warn("Admin gate DENY: self-fetch mismatch header={}:{} path={}:{}", app, emp, pathApp, pathEmp);
            return HeaderValidationFilter.writeError(exchange, HttpStatus.FORBIDDEN, "Can only fetch own permissions");
        }
        // validateToken 내부에서 에러를 false 로 흡수하므로 chain.filter 는 감싸지 않는다.
        return validateToken(exchange, app, emp)
                .flatMap(valid -> valid ? chain.filter(exchange)
                        : HeaderValidationFilter.writeError(exchange, HttpStatus.UNAUTHORIZED, "Token validation failed"));
    }

    // ── 관리 엔드포인트: 토큰 + admin role ────────────────────────
    private Mono<Void> requireAdmin(ServerWebExchange exchange, WebFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String app = headers.getFirst("app_name");
        String emp = headers.getFirst("employee_number");
        Mono<Void> precheck = checkHeaders(exchange, app, emp);
        if (precheck != null) {
            return precheck;
        }
        // 게이트 결정(토큰+role)은 chain.filter 와 분리한다. 그래야 다운스트림 컨트롤러가 낸
        // 정상 에러(예: 409 CONFLICT)가 이 필터의 에러 처리에 삼켜지지 않는다.
        return adminDecision(exchange, app, emp).flatMap(decision -> switch (decision) {
            case ALLOW -> chain.filter(exchange);
            case FORBIDDEN -> {
                log.warn("Admin gate DENY: not admin app={} emp={} path={}",
                        app, emp, exchange.getRequest().getURI().getPath());
                yield HeaderValidationFilter.writeError(exchange, HttpStatus.FORBIDDEN, "Admin role required");
            }
            case UNAUTHORIZED -> HeaderValidationFilter.writeError(exchange, HttpStatus.UNAUTHORIZED, "Token validation failed");
        });
    }

    private enum Decision { ALLOW, UNAUTHORIZED, FORBIDDEN }

    private Mono<Decision> adminDecision(ServerWebExchange exchange, String app, String emp) {
        return validateToken(exchange, app, emp)
                .flatMap(valid -> {
                    if (!valid) {
                        return Mono.just(Decision.UNAUTHORIZED);
                    }
                    return permissionService.forUser(app, emp)
                            .map(resp -> resp.permissions().stream()
                                    .anyMatch(e -> "admin".equalsIgnoreCase(e.role())))
                            .map(isAdmin -> isAdmin ? Decision.ALLOW : Decision.FORBIDDEN)
                            .onErrorReturn(Decision.UNAUTHORIZED);
                });
    }

    /** Authorization/app_name/employee_number 헤더 검증. 문제 있으면 에러 응답 Mono, 정상이면 null. */
    private Mono<Void> checkHeaders(ServerWebExchange exchange, String app, String emp) {
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return HeaderValidationFilter.writeError(exchange, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }
        if (app == null || app.isBlank()) {
            return HeaderValidationFilter.writeError(exchange, HttpStatus.BAD_REQUEST, "Missing required header: app_name");
        }
        if (emp == null || emp.isBlank()) {
            return HeaderValidationFilter.writeError(exchange, HttpStatus.BAD_REQUEST, "Missing required header: employee_number");
        }
        return null;
    }

    /** auth-server /validate (connector 없이) 로 토큰 서명·만료·claim↔헤더 일치 검증. */
    private Mono<Boolean> validateToken(ServerWebExchange exchange, String app, String emp) {
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        Map<String, String> body = Map.of("app_name", app, "employee_number", emp);
        return authServiceWebClient.post()
                .uri("/api/auth/validate")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(resp -> Boolean.TRUE.equals(resp.get("valid")))
                // 토큰 무효/만료/통신오류는 "검증 실패(false)" 로 흡수 — 다운스트림 에러와 섞이지 않게.
                .onErrorResume(ex -> {
                    log.warn("Admin gate token validation error: {}", ex.getMessage());
                    return Mono.just(false);
                });
    }
}
