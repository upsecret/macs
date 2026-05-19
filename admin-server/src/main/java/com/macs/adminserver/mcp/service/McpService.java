package com.macs.adminserver.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.macs.adminserver.mcp.client.McpJsonRpcClient;
import com.macs.adminserver.mcp.domain.McpServer;
import com.macs.adminserver.mcp.dto.McpServerRequest;
import com.macs.adminserver.mcp.dto.McpServerResponse;
import com.macs.adminserver.mcp.dto.McpToolCallRequest;
import com.macs.adminserver.mcp.dto.McpToolCallResponse;
import com.macs.adminserver.mcp.dto.McpToolResponse;
import com.macs.adminserver.mcp.repository.McpServerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
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

    public List<McpServerResponse> list() {
        return repository.findAll().stream().map(McpServerResponse::of).toList();
    }

    public McpServerResponse get(String id) {
        return McpServerResponse.of(findOrThrow(id));
    }

    @Transactional
    public McpServerResponse create(McpServerRequest req) {
        validate(req, true);
        if (repository.existsById(req.id())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "MCP server already exists with id: " + req.id());
        }
        McpServer saved = repository.save(new McpServer(
                req.id().trim(),
                req.name().trim(),
                emptyToNull(req.description()),
                req.endpointUrl().trim(),
                normalizeTransport(req.transport()),
                normalizeAuthType(req.authType()),
                emptyToNull(req.authToken()),
                normalizeSystem(req.system())
        ));
        log.info("MCP server CREATED id={} url={} authType={}",
                saved.getId(), saved.getEndpointUrl(), saved.getAuthType());
        return McpServerResponse.of(saved);
    }

    @Transactional
    public McpServerResponse update(String id, McpServerRequest req) {
        if (req.id() != null && !id.equals(req.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id cannot be changed");
        }
        McpServer entity = findOrThrow(id);

        // edit 모드: authToken 을 비우면 기존 토큰 유지 — UX 상 매번 토큰 재입력 강제 회피.
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
        McpServer saved = repository.save(entity);
        log.info("MCP server UPDATED id={} url={} authType={}",
                saved.getId(), saved.getEndpointUrl(), saved.getAuthType());
        return McpServerResponse.of(saved);
    }

    @Transactional
    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP server not found: " + id);
        }
        repository.deleteById(id);
        log.info("MCP server DELETED id={}", id);
    }

    /* ── MCP protocol passthrough ───────────────────────────── */

    public List<McpToolResponse> listTools(String id) {
        McpServer server = findOrThrow(id);
        JsonNode result = client.listTools(server);
        JsonNode tools = result.get("tools");
        List<McpToolResponse> out = new ArrayList<>();
        if (tools != null && tools.isArray()) {
            for (JsonNode t : tools) {
                out.add(new McpToolResponse(
                        t.path("name").asText(""),
                        t.path("description").asText(""),
                        t.get("inputSchema")
                ));
            }
        }
        return out;
    }

    public McpToolCallResponse callTool(String id, McpToolCallRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tool name is required");
        }
        McpServer server = findOrThrow(id);
        JsonNode result = client.callTool(server, req.name(), req.arguments());

        List<JsonNode> contentBlocks = new ArrayList<>();
        JsonNode content = result.get("content");
        if (content != null && content.isArray()) {
            content.forEach(contentBlocks::add);
        }
        boolean isError = result.path("isError").asBoolean(false);
        return new McpToolCallResponse(contentBlocks, isError, result);
    }

    /**
     * 헬스 체크 — initialize 호출 1회로 연결성/auth 검증.
     */
    public boolean healthCheck(String id) {
        McpServer server = findOrThrow(id);
        try {
            client.initialize(server);
            return true;
        } catch (Exception ex) {
            log.warn("MCP health check FAILED id={} err={}", id, ex.getMessage());
            return false;
        }
    }

    /* ── helpers ────────────────────────────────────────────── */

    private McpServer findOrThrow(String id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP server not found: " + id));
    }

    private void validate(McpServerRequest req, boolean requireId) {
        validateCommon(req, requireId);
        if ("bearer".equals(normalizeAuthType(req.authType()))
                && (req.authToken() == null || req.authToken().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "authToken is required when authType=bearer");
        }
    }

    /** edit 모드: resolvedToken (incoming 또는 기존 entity) 으로 검증. */
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
