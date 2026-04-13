package com.macs.adminserver.connector.service;

import com.macs.adminserver.connector.domain.Connector;
import com.macs.adminserver.connector.dto.AvailableRouteResponse;
import com.macs.adminserver.connector.dto.ConnectorRequest;
import com.macs.adminserver.connector.dto.ConnectorResponse;
import com.macs.adminserver.connector.repository.ConnectorRepository;
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
                docsUrl));

        log.info("Connector CREATED id={} type={} docsUrl={}",
                saved.getId(), saved.getType(), saved.getDocsUrl() != null ? saved.getDocsUrl() : "default");
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
        Connector entity = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Connector not found: " + id));
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setType(request.type());
        entity.setDocsUrl(normalizeDocsUrl(request.docsUrl()));
        Connector saved = repository.save(entity);

        log.info("Connector UPDATED id={} type={} docsUrl={}",
                saved.getId(), saved.getType(), saved.getDocsUrl() != null ? saved.getDocsUrl() : "default");
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
