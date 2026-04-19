package com.macs.adminserver.connector.service;

import com.macs.adminserver.connector.domain.Connector;
import com.macs.adminserver.connector.dto.AvailableRouteResponse;
import com.macs.adminserver.connector.dto.ConnectorRequest;
import com.macs.adminserver.connector.dto.ConnectorResponse;
import com.macs.adminserver.connector.dto.RouteMetadataResponse;
import com.macs.adminserver.connector.repository.ConnectorRepository;
import com.macs.adminserver.property.dto.GatewayDefinition;
import com.macs.adminserver.property.dto.RouteResponse;
import com.macs.adminserver.property.service.ConfigPropertyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ConnectorService {

    private static final Logger log = LoggerFactory.getLogger(ConnectorService.class);

    private static final String GATEWAY_APP = "gateway-service";
    private static final String GATEWAY_PROFILE = "default";
    private static final String GATEWAY_LABEL = "main";

    // docsUrl 이 비어있을 때 fallback: route 의 target URI + 이 suffix.
    private static final String DEFAULT_DOCS_SUFFIX = "/v3/api-docs";

    private static final Set<String> ALLOWED_TYPES = Set.of("agent", "api", "mcp");

    private final ConnectorRepository repository;
    private final ConfigPropertyService configPropertyService;
    private final RestClient apiDocsClient;

    public ConnectorService(ConnectorRepository repository,
                            ConfigPropertyService configPropertyService,
                            RestClient apiDocsClient) {
        this.repository = repository;
        this.configPropertyService = configPropertyService;
        this.apiDocsClient = apiDocsClient;
    }

    public List<ConnectorResponse> list() {
        Map<String, String> routeUriById = currentRouteUriById();
        return repository.findAll().stream()
                .map(c -> ConnectorResponse.of(
                        c,
                        routeUriById.containsKey(c.getId()),
                        routeUriById.get(c.getId())))
                .toList();
    }

    public List<AvailableRouteResponse> availableRoutes() {
        Set<String> usedIds = repository.findAll().stream()
                .map(Connector::getId)
                .collect(Collectors.toCollection(HashSet::new));
        return configPropertyService
                .findRoutes(GATEWAY_APP, GATEWAY_PROFILE, GATEWAY_LABEL)
                .stream()
                .filter(r -> !usedIds.contains(r.id()))
                .map(r -> new AvailableRouteResponse(r.id(), r.uri()))
                .toList();
    }

    @Transactional
    public ConnectorResponse create(ConnectorRequest request) {
        validateType(request.type());
        if (request.id() == null || request.id().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id is required");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
        }
        String system = normalizeSystem(request.system());
        if (repository.existsById(request.id())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Connector already registered for id: " + request.id());
        }
        Map<String, String> routeUriById = currentRouteUriById();
        if (!routeUriById.containsKey(request.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No matching gateway route for id: " + request.id());
        }

        String docsUrl = normalizeDocsUrl(request.docsUrl());
        Connector saved = repository.save(new Connector(
                request.id(),
                request.title(),
                request.description(),
                request.type(),
                system,
                docsUrl));

        log.info("Connector CREATED id={} type={} system={} docsUrl={}",
                saved.getId(), saved.getType(), saved.getSystem(),
                saved.getDocsUrl() != null ? saved.getDocsUrl() : "default");
        return ConnectorResponse.of(saved, true, routeUriById.get(saved.getId()));
    }

    @Transactional
    public ConnectorResponse update(String id, ConnectorRequest request) {
        if (request.id() != null && !id.equals(request.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id cannot be changed");
        }
        validateType(request.type());
        if (request.title() == null || request.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
        }
        String system = normalizeSystem(request.system());
        Connector entity = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Connector not found: " + id));
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setType(request.type());
        entity.setSystem(system);
        entity.setDocsUrl(normalizeDocsUrl(request.docsUrl()));
        Connector saved = repository.save(entity);

        log.info("Connector UPDATED id={} type={} system={} docsUrl={}",
                saved.getId(), saved.getType(), saved.getSystem(),
                saved.getDocsUrl() != null ? saved.getDocsUrl() : "default");
        Map<String, String> routeUriById = currentRouteUriById();
        return ConnectorResponse.of(saved,
                routeUriById.containsKey(saved.getId()),
                routeUriById.get(saved.getId()));
    }

    @Transactional
    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Connector not found: " + id);
        }
        repository.deleteById(id);
        log.info("Connector DELETED id={}", id);
    }

    // ── Route metadata (API Docs 경로 변환용) ──────────────────

    /**
     * 커넥터에 연결된 gateway route 의 Path predicate / StripPrefix / RewritePath 규칙을
     * 요약해서 반환. portal API Docs 가 upstream 경로 → gateway 경로로 변환할 때 사용.
     */
    public RouteMetadataResponse getRouteMetadata(String id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Connector not found: " + id);
        }
        RouteResponse route = configPropertyService
                .findRoutes(GATEWAY_APP, GATEWAY_PROFILE, GATEWAY_LABEL)
                .stream()
                .filter(r -> id.equals(r.id()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No gateway route for connector: " + id));

        return new RouteMetadataResponse(
                extractPathPredicate(route.predicates()),
                extractStripPrefix(route.filters()),
                extractRewriteRules(route.filters()));
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

    static Integer extractStripPrefix(List<GatewayDefinition> filters) {
        if (filters == null) return null;
        return filters.stream()
                .filter(f -> "StripPrefix".equalsIgnoreCase(f.name()))
                .map(f -> firstArg(f.args()))
                .filter(v -> v != null && !v.isBlank())
                .map(v -> {
                    try {
                        return Integer.parseInt(v.trim());
                    } catch (NumberFormatException ex) {
                        log.warn("StripPrefix value not numeric: {}", v);
                        return null;
                    }
                })
                .filter(v -> v != null)
                .findFirst()
                .orElse(null);
    }

    static List<RouteMetadataResponse.RewriteRule> extractRewriteRules(List<GatewayDefinition> filters) {
        List<RouteMetadataResponse.RewriteRule> rules = new ArrayList<>();
        if (filters == null) return rules;
        for (GatewayDefinition f : filters) {
            if (!"RewritePath".equalsIgnoreCase(f.name())) continue;
            Map<String, String> args = f.args();
            if (args == null || args.isEmpty()) continue;
            List<String> values = new ArrayList<>(args.values());
            // Key 우선순위: regexp/replacement 명시 → 없으면 _genkey_0, _genkey_1 순서 사용.
            String regexp = args.get("regexp");
            String replacement = args.get("replacement");
            if (regexp == null && values.size() >= 1) regexp = values.get(0);
            if (replacement == null && values.size() >= 2) replacement = values.get(1);
            if (regexp != null && replacement != null) {
                rules.add(new RouteMetadataResponse.RewriteRule(regexp.trim(), replacement.trim()));
            }
        }
        return rules;
    }

    private static String firstArg(Map<String, String> args) {
        if (args == null || args.isEmpty()) return null;
        return args.values().iterator().next();
    }

    // ── OpenAPI 문서 프록시 ──────────────────────────────────────

    /**
     * 커넥터에 연결된 OpenAPI JSON 을 가져온다.
     * - docsUrl 이 있으면 그 URL 에 직접 GET
     * - 없으면 route 의 target URI + {@code /v3/api-docs}
     * CORS 를 피하기 위해 admin-server 가 프록시 역할.
     */
    public String fetchApiDocs(String id) {
        Connector connector = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Connector not found: " + id));

        String target = connector.getDocsUrl();
        boolean isDefault = target == null || target.isBlank();
        if (isDefault) {
            String routeUri = currentRouteUriById().get(id);
            if (routeUri == null || routeUri.isBlank()) {
                log.warn("api-docs fallback failed: no gateway route for connector={}", id);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "No gateway route URI for connector: " + id);
            }
            target = stripTrailingSlash(routeUri) + DEFAULT_DOCS_SUFFIX;
        }

        log.info("Fetching api-docs for connector={} from {} (default={})",
                id, target, isDefault);
        try {
            String body = apiDocsClient.get()
                    .uri(target)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                log.warn("api-docs empty body connector={} url={}", id, target);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Upstream returned empty body");
            }
            return body;
        } catch (RestClientException ex) {
            log.error("api-docs fetch failed connector={} url={} type={} msg={}",
                    id, target, ex.getClass().getSimpleName(), ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to fetch api-docs: " + ex.getMessage());
        }
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private String normalizeSystem(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "system is required");
        }
        return raw.trim();
    }

    private String normalizeDocsUrl(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        if (!(trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "docsUrl must start with http:// or https:// if provided");
        }
        return trimmed;
    }

    private Map<String, String> currentRouteUriById() {
        return configPropertyService
                .findRoutes(GATEWAY_APP, GATEWAY_PROFILE, GATEWAY_LABEL)
                .stream()
                .collect(Collectors.toMap(RouteResponse::id, RouteResponse::uri, (a, b) -> a));
    }

    private void validateType(String type) {
        if (type == null || !ALLOWED_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "type must be one of " + ALLOWED_TYPES);
        }
    }
}
