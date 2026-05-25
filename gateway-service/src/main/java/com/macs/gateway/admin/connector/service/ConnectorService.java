package com.macs.gateway.admin.connector.service;

import com.macs.gateway.admin.connector.domain.Connector;
import com.macs.gateway.admin.connector.dto.AvailableRouteResponse;
import com.macs.gateway.admin.connector.dto.ConnectorRequest;
import com.macs.gateway.admin.connector.dto.ConnectorResponse;
import com.macs.gateway.admin.connector.dto.RouteMetadataResponse;
import com.macs.gateway.admin.connector.repository.ConnectorRepository;
import com.macs.gateway.admin.property.dto.GatewayDefinition;
import com.macs.gateway.admin.property.dto.RouteResponse;
import com.macs.gateway.admin.property.service.ConfigPropertyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ConnectorService {

    private static final Logger log = LoggerFactory.getLogger(ConnectorService.class);

    private static final String GATEWAY_APP = "gateway-service";
    private static final String GATEWAY_PROFILE = "default";
    private static final String GATEWAY_LABEL = "main";
    private static final String DEFAULT_DOCS_SUFFIX = "/v3/api-docs";

    private static final Set<String> ALLOWED_TYPES = Set.of("agent", "api", "mcp");

    private final ConnectorRepository repository;
    private final ConfigPropertyService configPropertyService;
    private final WebClient apiDocsWebClient;

    public ConnectorService(ConnectorRepository repository,
                            ConfigPropertyService configPropertyService,
                            @Qualifier("apiDocsWebClient") WebClient apiDocsWebClient) {
        this.repository = repository;
        this.configPropertyService = configPropertyService;
        this.apiDocsWebClient = apiDocsWebClient;
    }

    public Flux<ConnectorResponse> list() {
        return currentRouteUriById().flatMapMany(routeUriById ->
                repository.findAll().map(c -> ConnectorResponse.of(
                        c,
                        routeUriById.containsKey(c.getId()),
                        routeUriById.get(c.getId()))));
    }

    public Flux<AvailableRouteResponse> availableRoutes() {
        return repository.findAll()
                .map(Connector::getId)
                .collect(Collectors.toCollection(HashSet::new))
                .flatMapMany(usedIds -> configPropertyService
                        .findRoutes(GATEWAY_APP, GATEWAY_PROFILE, GATEWAY_LABEL)
                        .flatMapMany(Flux::fromIterable)
                        .filter(r -> !usedIds.contains(r.id()))
                        .map(r -> new AvailableRouteResponse(r.id(), r.uri())));
    }

    public Mono<ConnectorResponse> create(ConnectorRequest request) {
        return Mono.fromRunnable(() -> {
                    validateType(request.type());
                    if (request.id() == null || request.id().isBlank()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id is required");
                    }
                    if (request.title() == null || request.title().isBlank()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
                    }
                })
                .then(Mono.defer(() -> {
                    String system = normalizeSystem(request.system());
                    return repository.existsById(request.id()).flatMap(exists -> {
                        if (exists) {
                            return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                    "Connector already registered for id: " + request.id()));
                        }
                        return currentRouteUriById().flatMap(routeUriById -> {
                            if (!routeUriById.containsKey(request.id())) {
                                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "No matching gateway route for id: " + request.id()));
                            }
                            String docsUrl = normalizeDocsUrl(request.docsUrl());
                            return repository.insert(new Connector(
                                            request.id(), request.title(), request.description(),
                                            request.type(), system, docsUrl))
                                    .doOnNext(saved -> log.info(
                                            "Connector CREATED id={} type={} system={} docsUrl={}",
                                            saved.getId(), saved.getType(), saved.getSystem(),
                                            saved.getDocsUrl() != null ? saved.getDocsUrl() : "default"))
                                    .map(saved -> ConnectorResponse.of(saved, true,
                                            routeUriById.get(saved.getId())));
                        });
                    });
                }));
    }

    public Mono<ConnectorResponse> update(String id, ConnectorRequest request) {
        return Mono.fromRunnable(() -> {
                    if (request.id() != null && !id.equals(request.id())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id cannot be changed");
                    }
                    validateType(request.type());
                    if (request.title() == null || request.title().isBlank()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
                    }
                })
                .then(Mono.defer(() -> {
                    String system = normalizeSystem(request.system());
                    return repository.findById(id)
                            .switchIfEmpty(Mono.error(new ResponseStatusException(
                                    HttpStatus.NOT_FOUND, "Connector not found: " + id)))
                            .flatMap(entity -> {
                                entity.setTitle(request.title());
                                entity.setDescription(request.description());
                                entity.setType(request.type());
                                entity.setSystem(system);
                                entity.setDocsUrl(normalizeDocsUrl(request.docsUrl()));
                                return repository.update(entity);
                            })
                            .doOnNext(saved -> log.info(
                                    "Connector UPDATED id={} type={} system={} docsUrl={}",
                                    saved.getId(), saved.getType(), saved.getSystem(),
                                    saved.getDocsUrl() != null ? saved.getDocsUrl() : "default"))
                            .flatMap(saved -> currentRouteUriById().map(routeUriById ->
                                    ConnectorResponse.of(saved,
                                            routeUriById.containsKey(saved.getId()),
                                            routeUriById.get(saved.getId()))));
                }));
    }

    public Mono<Void> delete(String id) {
        return repository.existsById(id).flatMap(exists -> {
            if (!exists) {
                return Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Connector not found: " + id));
            }
            return repository.deleteById(id)
                    .doOnSuccess(v -> log.info("Connector DELETED id={}", id));
        });
    }

    // ── Route metadata ─────────────────────────────────────────

    public Mono<RouteMetadataResponse> getRouteMetadata(String id) {
        return repository.existsById(id).flatMap(exists -> {
            if (!exists) {
                return Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Connector not found: " + id));
            }
            return configPropertyService.findRoutes(GATEWAY_APP, GATEWAY_PROFILE, GATEWAY_LABEL)
                    .flatMap(routes -> routes.stream()
                            .filter(r -> id.equals(r.id()))
                            .findFirst()
                            .map(Mono::just)
                            .orElse(Mono.error(new ResponseStatusException(
                                    HttpStatus.NOT_FOUND, "No gateway route for connector: " + id))))
                    .map(route -> new RouteMetadataResponse(
                            extractPathPredicate(route.predicates()),
                            extractStripPrefix(route.filters()),
                            extractRewriteRules(route.filters())));
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

    // ── OpenAPI 문서 프록시 (WebClient) ──────────────────────────

    public Mono<String> fetchApiDocs(String id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Connector not found: " + id)))
                .flatMap(connector -> resolveDocsUrl(connector)
                        .flatMap(target -> {
                            log.info("Fetching api-docs for connector={} from {}", id, target);
                            return apiDocsWebClient.get()
                                    .uri(target)
                                    .retrieve()
                                    .bodyToMono(String.class)
                                    .flatMap(body -> {
                                        if (body == null || body.isBlank()) {
                                            log.warn("api-docs empty body connector={} url={}", id, target);
                                            return Mono.error(new ResponseStatusException(
                                                    HttpStatus.BAD_GATEWAY,
                                                    "Upstream returned empty body"));
                                        }
                                        return Mono.just(body);
                                    })
                                    .onErrorResume(WebClientException.class, ex -> {
                                        log.error("api-docs fetch failed connector={} url={} type={} msg={}",
                                                id, target, ex.getClass().getSimpleName(), ex.getMessage());
                                        return Mono.error(new ResponseStatusException(
                                                HttpStatus.BAD_GATEWAY,
                                                "Failed to fetch api-docs: " + ex.getMessage()));
                                    });
                        }));
    }

    private Mono<String> resolveDocsUrl(Connector connector) {
        String docsUrl = connector.getDocsUrl();
        if (docsUrl != null && !docsUrl.isBlank()) {
            return Mono.just(docsUrl);
        }
        return currentRouteUriById().flatMap(routeUriById -> {
            String routeUri = routeUriById.get(connector.getId());
            if (routeUri == null || routeUri.isBlank()) {
                log.warn("api-docs fallback failed: no gateway route for connector={}", connector.getId());
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "No gateway route URI for connector: " + connector.getId()));
            }
            return Mono.just(stripTrailingSlash(routeUri) + DEFAULT_DOCS_SUFFIX);
        });
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

    private Mono<Map<String, String>> currentRouteUriById() {
        return configPropertyService.findRoutes(GATEWAY_APP, GATEWAY_PROFILE, GATEWAY_LABEL)
                .map(routes -> routes.stream()
                        .collect(Collectors.toMap(RouteResponse::id, RouteResponse::uri, (a, b) -> a)));
    }

    private void validateType(String type) {
        if (type == null || !ALLOWED_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "type must be one of " + ALLOWED_TYPES);
        }
    }
}
