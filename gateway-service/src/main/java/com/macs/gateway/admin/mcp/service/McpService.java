package com.macs.gateway.admin.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.List;
import java.util.Set;

@Service
public class McpService {

    private static final Logger log = LoggerFactory.getLogger(McpService.class);

    private static final Set<String> ALLOWED_TRANSPORTS = Set.of("streamable-http");
    private static final Set<String> ALLOWED_AUTH_TYPES = Set.of("none", "bearer");

    private final McpServerRepository repository;
    private final McpJsonRpcClient client;

    public McpService(McpServerRepository repository, McpJsonRpcClient client) {
        this.repository = repository;
        this.client = client;
    }

    public Flux<McpServerResponse> list() {
        return repository.findAll().map(McpServerResponse::of);
    }

    public Mono<McpServerResponse> get(String id) {
        return findOrError(id).map(McpServerResponse::of);
    }

    public Mono<McpServerResponse> create(McpServerRequest req) {
        return Mono.fromRunnable(() -> validate(req, true))
                .then(Mono.defer(() -> repository.existsById(req.id()).flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "MCP server already exists with id: " + req.id()));
                    }
                    return repository.insert(new McpServer(
                                    req.id().trim(),
                                    req.name().trim(),
                                    emptyToNull(req.description()),
                                    req.endpointUrl().trim(),
                                    normalizeTransport(req.transport()),
                                    normalizeAuthType(req.authType()),
                                    emptyToNull(req.authToken()),
                                    normalizeSystem(req.system())))
                            .doOnNext(saved -> log.info(
                                    "MCP server CREATED id={} url={} authType={}",
                                    saved.getId(), saved.getEndpointUrl(), saved.getAuthType()))
                            .map(McpServerResponse::of);
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

                    entity.setName(req.name().trim());
                    entity.setDescription(emptyToNull(req.description()));
                    entity.setEndpointUrl(req.endpointUrl().trim());
                    entity.setTransport(normalizeTransport(req.transport()));
                    String at = normalizeAuthType(req.authType());
                    entity.setAuthType(at);
                    // authType=none 으로 바꾸면 기존 토큰 폐기. bearer 면 resolvedToken 사용.
                    entity.setAuthToken("bearer".equals(at) ? resolvedToken : null);
                    entity.setSystem(normalizeSystem(req.system()));
                    return repository.update(entity)
                            .doOnNext(saved -> log.info(
                                    "MCP server UPDATED id={} url={} authType={}",
                                    saved.getId(), saved.getEndpointUrl(), saved.getAuthType()))
                            .map(McpServerResponse::of);
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

    /* ── MCP protocol passthrough ───────────────────────────── */

    public Flux<McpToolResponse> listTools(String id) {
        return findOrError(id)
                .flatMap(client::listTools)
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

    public Mono<McpToolCallResponse> callTool(String id, McpToolCallRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "tool name is required"));
        }
        return findOrError(id)
                .flatMap(server -> client.callTool(server, req.name(), req.arguments()))
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

    /** initialize 호출 1회로 연결성/auth 검증. */
    public Mono<Boolean> healthCheck(String id) {
        return findOrError(id)
                .flatMap(server -> client.initialize(server).thenReturn(true))
                .onErrorResume(ex -> {
                    log.warn("MCP health check FAILED id={} err={}", id, ex.getMessage());
                    return Mono.just(false);
                });
    }

    /* ── helpers ────────────────────────────────────────────── */

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
        if (req.endpointUrl() == null || req.endpointUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endpointUrl is required");
        }
        String url = req.endpointUrl().trim();
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "endpointUrl must start with http:// or https://");
        }
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
