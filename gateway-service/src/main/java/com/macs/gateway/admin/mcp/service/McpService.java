package com.macs.gateway.admin.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.macs.gateway.admin.connector.dto.AvailableRouteResponse;
import com.macs.gateway.admin.mcp.client.McpJsonRpcClient;
import com.macs.gateway.admin.mcp.domain.McpServer;
import com.macs.gateway.admin.mcp.dto.McpServerRequest;
import com.macs.gateway.admin.mcp.dto.McpServerResponse;
import com.macs.gateway.admin.mcp.dto.McpToolCallRequest;
import com.macs.gateway.admin.mcp.dto.McpToolCallResponse;
import com.macs.gateway.admin.mcp.dto.McpToolResponse;
import com.macs.gateway.admin.mcp.repository.McpServerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class McpService {

    private static final Logger log = LoggerFactory.getLogger(McpService.class);

    private static final Set<String> ALLOWED_TRANSPORTS = Set.of("streamable-http");
    private static final Set<String> ALLOWED_AUTH_TYPES = Set.of("none", "bearer");
    /** MCP 라우트로 인정하는 Path prefix — available-routes 필터·연동 규약. */
    private static final String MCP_PATH_PREFIX = "/mcp/";

    private final McpServerRepository repository;
    private final McpJsonRpcClient client;
    private final McpRouteResolver routeResolver;

    public McpService(McpServerRepository repository, McpJsonRpcClient client,
                      McpRouteResolver routeResolver) {
        this.repository = repository;
        this.client = client;
        this.routeResolver = routeResolver;
    }

    public Flux<McpServerResponse> list() {
        return routeResolver.routesById().flatMapMany(routesById ->
                repository.findAll().map(s ->
                        McpServerResponse.of(s, routeResolver.pathOf(routesById.get(s.getId())))));
    }

    public Mono<McpServerResponse> get(String id) {
        return findOrError(id).flatMap(s -> routeResolver.findRoute(id)
                .map(route -> McpServerResponse.of(s, routeResolver.pathOf(route)))
                .defaultIfEmpty(McpServerResponse.of(s, null)));
    }

    /** 커넥터 연동에서 MCP 서버에 연결할 수 있는 (아직 미등록) 게이트웨이 MCP 라우트. */
    public Flux<AvailableRouteResponse> availableRoutes() {
        return repository.findAll()
                .map(McpServer::getId)
                .collect(HashSet::new, HashSet::add)
                .flatMapMany(usedIds -> routeResolver.routesById()
                        .flatMapMany(routesById -> Flux.fromIterable(routesById.values()))
                        .filter(r -> !usedIds.contains(r.id()))
                        .filter(r -> {
                            String path = routeResolver.pathOf(r);
                            return path != null && path.startsWith(MCP_PATH_PREFIX);
                        })
                        .map(r -> new AvailableRouteResponse(r.id(), r.uri())));
    }

    public Mono<McpServerResponse> create(McpServerRequest req) {
        return Mono.fromRunnable(() -> validate(req, true))
                .then(Mono.defer(() -> repository.existsById(req.id().trim()).flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "MCP server already exists with id: " + req.id()));
                    }
                    // 라우트가 단일 소스: 매칭 라우트가 있어야 등록 가능, endpointUrl 은 라우트 uri.
                    return routeResolver.resolveUpstreamUri(req.id().trim()).flatMap(upstream ->
                            repository.insert(new McpServer(
                                            req.id().trim(),
                                            req.name().trim(),
                                            emptyToNull(req.description()),
                                            upstream,
                                            normalizeTransport(req.transport()),
                                            normalizeAuthType(req.authType()),
                                            emptyToNull(req.authToken()),
                                            normalizeSystem(req.system())))
                                    .doOnNext(saved -> log.info(
                                            "MCP server CREATED id={} upstream={} authType={}",
                                            saved.getId(), saved.getEndpointUrl(), saved.getAuthType()))
                                    .flatMap(this::toResponseWithRoute));
                })));
    }

    public Mono<McpServerResponse> update(String id, McpServerRequest req) {
        return Mono.fromRunnable(() -> {
                    if (req.id() != null && !id.equals(req.id())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id cannot be changed");
                    }
                })
                .then(findOrError(id))
                .flatMap(entity -> {
                    // edit 모드: authToken 을 비우면 기존 토큰 유지.
                    String incomingToken = emptyToNull(req.authToken());
                    String resolvedToken = incomingToken != null ? incomingToken : entity.getAuthToken();
                    validateUpdate(req, resolvedToken);

                    // 업스트림은 라우트 uri 가 단일 소스 — 항상 라우트에서 재동기화.
                    return routeResolver.resolveUpstreamUri(id).flatMap(upstream -> {
                        entity.setName(req.name().trim());
                        entity.setDescription(emptyToNull(req.description()));
                        entity.setEndpointUrl(upstream);
                        entity.setTransport(normalizeTransport(req.transport()));
                        String at = normalizeAuthType(req.authType());
                        entity.setAuthType(at);
                        entity.setAuthToken("bearer".equals(at) ? resolvedToken : null);
                        entity.setSystem(normalizeSystem(req.system()));
                        return repository.update(entity)
                                .doOnNext(saved -> log.info(
                                        "MCP server UPDATED id={} upstream={} authType={}",
                                        saved.getId(), saved.getEndpointUrl(), saved.getAuthType()))
                                .flatMap(this::toResponseWithRoute);
                    });
                });
    }

    public Mono<Void> delete(String id) {
        return repository.existsById(id).flatMap(exists -> {
            if (!exists) {
                return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "MCP server not found: " + id));
            }
            return repository.deleteById(id)
                    .doOnSuccess(v -> log.info("MCP server DELETED id={}", id));
        });
    }

    /* ── MCP protocol passthrough (게이트웨이 라우트 loopback 경유) ── */

    public Flux<McpToolResponse> listTools(String id, CallerIdentity caller) {
        return findOrError(id)
                .flatMap(server -> routeResolver.resolveLoopbackUrl(id)
                        .flatMap(url -> client.listTools(id, url, effectiveCaller(server, caller))))
                .flatMapMany(result -> {
                    JsonNode tools = result.get("tools");
                    List<McpToolResponse> out = new ArrayList<>();
                    if (tools != null && tools.isArray()) {
                        for (JsonNode t : tools) {
                            out.add(new McpToolResponse(
                                    t.path("name").asText(""),
                                    t.path("description").asText(""),
                                    t.get("inputSchema")));
                        }
                    }
                    return Flux.fromIterable(out);
                });
    }

    public Mono<McpToolCallResponse> callTool(String id, McpToolCallRequest req, CallerIdentity caller) {
        if (req.name() == null || req.name().isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "tool name is required"));
        }
        return findOrError(id)
                .flatMap(server -> routeResolver.resolveLoopbackUrl(id)
                        .flatMap(url -> client.callTool(id, url, effectiveCaller(server, caller),
                                req.name(), req.arguments())))
                .map(result -> {
                    List<JsonNode> contentBlocks = new ArrayList<>();
                    JsonNode content = result.get("content");
                    if (content != null && content.isArray()) {
                        content.forEach(contentBlocks::add);
                    }
                    boolean isError = result.path("isError").asBoolean(false);
                    return new McpToolCallResponse(contentBlocks, isError, result);
                });
    }

    /** initialize 호출 1회로 연결성/auth 검증 (라우트 경유). */
    public Mono<Boolean> healthCheck(String id, CallerIdentity caller) {
        return findOrError(id)
                .flatMap(server -> routeResolver.resolveLoopbackUrl(id)
                        .flatMap(url -> client.openSession(id, url, effectiveCaller(server, caller)).thenReturn(true)))
                .onErrorResume(ex -> {
                    log.warn("MCP health check FAILED id={} err={}", id, ex.getMessage());
                    return Mono.just(false);
                });
    }

    /* ── helpers ────────────────────────────────────────────── */

    /**
     * authType=bearer 인 MCP 서버는 게이트웨이가 AuthValidation 으로 게이팅하지 않고,
     * 저장된 authToken 을 업스트림 Authorization 으로 전달한다 → MCP 서버가 자체 검증.
     * (그 외에는 호출자 신원(사용자 JWT)을 그대로 전달해 라우트 AuthValidation 이 인가.)
     */
    private CallerIdentity effectiveCaller(McpServer server, CallerIdentity caller) {
        if ("bearer".equalsIgnoreCase(server.getAuthType())
                && server.getAuthToken() != null && !server.getAuthToken().isBlank()) {
            return new CallerIdentity("Bearer " + server.getAuthToken(),
                    caller.appName(), caller.employeeNumber());
        }
        return caller;
    }

    private Mono<McpServerResponse> toResponseWithRoute(McpServer saved) {
        return routeResolver.findRoute(saved.getId())
                .map(route -> McpServerResponse.of(saved, routeResolver.pathOf(route)))
                .defaultIfEmpty(McpServerResponse.of(saved, null));
    }

    private Mono<McpServer> findOrError(String id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "MCP server not found: " + id)));
    }

    private void validate(McpServerRequest req, boolean requireId) {
        validateCommon(req, requireId);
        if ("bearer".equals(normalizeAuthType(req.authType()))
                && (req.authToken() == null || req.authToken().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "authToken is required when authType=bearer");
        }
    }

    private void validateUpdate(McpServerRequest req, String resolvedToken) {
        validateCommon(req, false);
        if ("bearer".equals(normalizeAuthType(req.authType()))
                && (resolvedToken == null || resolvedToken.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "authToken is required when authType=bearer");
        }
    }

    private void validateCommon(McpServerRequest req, boolean requireId) {
        if (requireId && (req.id() == null || req.id().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id is required");
        }
        if (req.name() == null || req.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        // endpointUrl 은 더 이상 입력받지 않는다 — 라우트 uri 가 단일 소스.
        String t = normalizeTransport(req.transport());
        if (!ALLOWED_TRANSPORTS.contains(t)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "transport must be one of " + ALLOWED_TRANSPORTS);
        }
        String at = normalizeAuthType(req.authType());
        if (!ALLOWED_AUTH_TYPES.contains(at)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "authType must be one of " + ALLOWED_AUTH_TYPES);
        }
    }

    private static String normalizeTransport(String s) {
        return (s == null || s.isBlank()) ? "streamable-http" : s.trim().toLowerCase();
    }

    private static String normalizeAuthType(String s) {
        return (s == null || s.isBlank()) ? "none" : s.trim().toLowerCase();
    }

    private static String normalizeSystem(String s) {
        return (s == null || s.isBlank()) ? "common" : s.trim();
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
