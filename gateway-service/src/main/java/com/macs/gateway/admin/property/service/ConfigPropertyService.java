package com.macs.gateway.admin.property.service;

import com.macs.gateway.admin.property.domain.ConfigProperty;
import com.macs.gateway.admin.property.domain.ConfigPropertyId;
import com.macs.gateway.admin.property.dto.GatewayDefinition;
import com.macs.gateway.admin.property.dto.PropertyRequest;
import com.macs.gateway.admin.property.dto.PropertyResponse;
import com.macs.gateway.admin.property.dto.RouteRequest;
import com.macs.gateway.admin.property.dto.RouteResponse;
import com.macs.gateway.admin.property.repository.ConfigPropertyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.bus.BusProperties;
import org.springframework.cloud.bus.event.RefreshRemoteApplicationEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ConfigPropertyService {

    private static final Logger log = LoggerFactory.getLogger(ConfigPropertyService.class);

    private static final String ROUTE_PREFIX = "spring.cloud.gateway.server.webflux.routes";
    private static final String SWAGGER_URLS_PREFIX = "springdoc.swagger-ui.urls";
    private static final Pattern ROUTE_INDEX_PATTERN =
            Pattern.compile("spring\\.cloud\\.gateway\\.server\\.webflux\\.routes\\[(\\d+)]\\..+");
    private static final Pattern SWAGGER_URL_INDEX_PATTERN =
            Pattern.compile("springdoc\\.swagger-ui\\.urls\\[(\\d+)]\\.(name|url)");
    private static final Pattern DEF_PATTERN =
            Pattern.compile("(predicates|filters)\\[(\\d+)](?:\\.(.+))?");
    private static final String API_DOCS_SUFFIX = "-api-docs";

    private final ConfigPropertyRepository repository;
    private final ApplicationContext applicationContext;
    private final BusProperties busProperties;

    public ConfigPropertyService(ConfigPropertyRepository repository,
                                 ApplicationContext applicationContext,
                                 BusProperties busProperties) {
        this.repository = repository;
        this.applicationContext = applicationContext;
        this.busProperties = busProperties;
    }

    // ── Property CRUD ───────────────────────────────────────────

    public Flux<PropertyResponse> findProperties(String application, String profile, String label) {
        return repository.findByApplicationAndProfileAndLabel(application, profile, label)
                .map(this::toPropertyResponse);
    }

    public Mono<PropertyResponse> createProperty(PropertyRequest request) {
        ConfigPropertyId id = toId(request);
        return repository.existsById(id).flatMap(exists -> {
            if (exists) {
                log.warn("Property CREATE conflict app={} profile={} label={} key={}",
                        request.application(), request.profile(), request.label(), request.propKey());
                return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Property already exists"));
            }
            return repository.upsert(new ConfigProperty(id, request.propValue()))
                    .doOnNext(p -> log.info("Property CREATED app={} profile={} label={} key={}",
                            request.application(), request.profile(), request.label(), request.propKey()))
                    .map(this::toPropertyResponse);
        });
    }

    public Mono<PropertyResponse> updateProperty(PropertyRequest request) {
        ConfigPropertyId id = toId(request);
        return repository.findById(id)
                .switchIfEmpty(Mono.error(() -> {
                    log.warn("Property UPDATE not-found app={} profile={} label={} key={}",
                            request.application(), request.profile(), request.label(), request.propKey());
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found");
                }))
                .flatMap(existing -> repository.upsert(new ConfigProperty(id, request.propValue())))
                .doOnNext(p -> log.info("Property UPDATED app={} profile={} label={} key={}",
                        request.application(), request.profile(), request.label(), request.propKey()))
                .map(this::toPropertyResponse);
    }

    public Mono<Void> deleteProperty(String application, String profile, String label, String propKey) {
        ConfigPropertyId id = new ConfigPropertyId(application, profile, label, propKey);
        return repository.existsById(id).flatMap(exists -> {
            if (!exists) {
                log.warn("Property DELETE not-found app={} profile={} label={} key={}",
                        application, profile, label, propKey);
                return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found"));
            }
            return repository.deleteById(id)
                    .doOnSuccess(v -> log.info("Property DELETED app={} profile={} label={} key={}",
                            application, profile, label, propKey));
        });
    }

    // ── Route CRUD ──────────────────────────────────────────────

    public Mono<List<RouteResponse>> findRoutes(String application, String profile, String label) {
        return repository.findByKeyPattern(application, profile, label, ROUTE_PREFIX + "[%")
                .collectList()
                .map(this::parseRoutes);
    }

    public Mono<RouteResponse> createRoute(String application, String profile, String label,
                                           RouteRequest request) {
        return nextRouteIndex(application, profile, label)
                .flatMap(nextIndex -> saveRouteProperties(application, profile, label, nextIndex, request)
                        .then(Mono.defer(() -> {
                            boolean swagger = shouldRegisterSwagger(request);
                            if (!swagger) {
                                log.info("Route CREATED app={} profile={} label={} id={} uri={} index={} swagger=false",
                                        application, profile, label, request.id(), request.uri(), nextIndex);
                                return Mono.empty();
                            }
                            return nextRouteIndex(application, profile, label)
                                    .flatMap(docsIndex -> saveRouteProperties(application, profile, label,
                                            docsIndex, buildApiDocsRoute(request.id(), request.uri()))
                                            .then(addSwaggerUrlEntry(application, profile, label, request.id()))
                                            .doOnSuccess(v -> log.info(
                                                    "Route CREATED app={} profile={} label={} id={} uri={} index={} swagger=true",
                                                    application, profile, label, request.id(),
                                                    request.uri(), nextIndex)));
                        })))
                .thenReturn(toRouteResponse(request));
    }

    public Mono<RouteResponse> updateRoute(String routeId, String application, String profile,
                                           String label, RouteRequest request) {
        return findRouteIndexOrError(routeId, application, profile, label)
                .flatMap(index -> repository.deleteByKeyPattern(application, profile, label,
                                ROUTE_PREFIX + "[" + index + "].%")
                        .then(saveRouteProperties(application, profile, label, index, request))
                        // companion api-docs route sync
                        .then(findRouteIndex(routeId + API_DOCS_SUFFIX, application, profile, label)
                                .flatMap(docsIdx -> repository.deleteByKeyPattern(application, profile, label,
                                                ROUTE_PREFIX + "[" + docsIdx + "].%")
                                        .then(saveRouteProperties(application, profile, label, docsIdx,
                                                buildApiDocsRoute(routeId, request.uri())))
                                        .thenReturn(true))
                                .defaultIfEmpty(false)
                                .doOnNext(synced -> log.info(
                                        "Route UPDATED app={} profile={} label={} id={} newUri={} docsSynced={}",
                                        application, profile, label, routeId, request.uri(), synced))))
                .thenReturn(toRouteResponse(request));
    }

    public Mono<Void> deleteRoute(String routeId, String application, String profile, String label) {
        return findRouteIndexOrError(routeId, application, profile, label)
                .flatMap(index -> repository.deleteByKeyPattern(application, profile, label,
                        ROUTE_PREFIX + "[" + index + "].%"))
                .then(findRouteIndex(routeId + API_DOCS_SUFFIX, application, profile, label)
                        .flatMap(docsIdx -> repository.deleteByKeyPattern(application, profile, label,
                                        ROUTE_PREFIX + "[" + docsIdx + "].%")
                                .thenReturn(true))
                        .defaultIfEmpty(false)
                        .flatMap(removed -> removeSwaggerUrlEntry(application, profile, label, routeId)
                                .doOnSuccess(v -> log.info(
                                        "Route DELETED app={} profile={} label={} id={} docsRemoved={}",
                                        application, profile, label, routeId, removed))));
    }

    // ── Refresh ─────────────────────────────────────────────────

    /**
     * Spring Cloud Bus refresh — broadcasts to all bus participants so each gateway
     * re-fetches its routes from Spring Cloud Config (admin-server). Same behavior as
     * the original admin-server's POST /api/config/properties/refresh endpoint.
     */
    public void publishRefreshEvent() {
        // originService 는 BusProperties.getId() 를 써야 한다 — applicationContext.getId() 면
        // Spring Cloud Bus 의 acceptLocal 핸들러가 isFromSelf 검사에서 false 로 떨어져
        // outbound channel 로 안 흘러간다. 표준 /actuator/busrefresh 도 동일.
        String origin = busProperties.getId();
        log.info("Publishing RefreshRemoteApplicationEvent destination=** origin={}", origin);
        applicationContext.publishEvent(
                new RefreshRemoteApplicationEvent(this, origin, "**"));
    }

    // ════════════════════════════════════════════════════════════
    //  Route 저장 — shorthand 가능하면 한 줄, 아니면 name+args 구조
    // ════════════════════════════════════════════════════════════

    private Mono<Void> saveRouteProperties(String app, String profile, String label,
                                            int index, RouteRequest request) {
        String p = ROUTE_PREFIX + "[" + index + "].";
        Map<String, String> kvs = new LinkedHashMap<>();
        kvs.put(p + "id", request.id());
        kvs.put(p + "uri", request.uri());
        if (request.order() != null) {
            kvs.put(p + "order", String.valueOf(request.order()));
        }
        appendDefs(kvs, p + "predicates", nullSafe(request.predicates()));
        appendDefs(kvs, p + "filters", nullSafe(request.filters()));
        return saveAll(app, profile, label, kvs);
    }

    private static void appendDefs(Map<String, String> kvs, String prefix, List<GatewayDefinition> defs) {
        for (int i = 0; i < defs.size(); i++) {
            GatewayDefinition def = defs.get(i);
            String dp = prefix + "[" + i + "]";
            String shorthand = def.toShorthand();
            if (shorthand != null) {
                kvs.put(dp, shorthand);
            } else {
                kvs.put(dp + ".name", def.name());
                for (Map.Entry<String, String> arg : def.args().entrySet()) {
                    kvs.put(dp + ".args." + arg.getKey(), arg.getValue());
                }
            }
        }
    }

    private Mono<Void> saveAll(String app, String profile, String label, Map<String, String> kvs) {
        return Flux.fromIterable(kvs.entrySet())
                .concatMap(e -> repository.upsert(new ConfigProperty(
                        new ConfigPropertyId(app, profile, label, e.getKey()), e.getValue())))
                .then();
    }

    // ════════════════════════════════════════════════════════════
    //  Route 파싱 — shorthand + structured 모두 지원
    // ════════════════════════════════════════════════════════════

    private List<RouteResponse> parseRoutes(List<ConfigProperty> props) {
        Map<Integer, Map<String, String>> routeMap = new TreeMap<>();
        for (ConfigProperty prop : props) {
            int idx = extractRouteIndex(prop.getId().propKey());
            if (idx >= 0) {
                String field = prop.getId().propKey()
                        .substring((ROUTE_PREFIX + "[" + idx + "].").length());
                routeMap.computeIfAbsent(idx, k -> new LinkedHashMap<>())
                        .put(field, prop.getPropValue());
            }
        }
        return routeMap.values().stream().map(this::parseOneRoute).toList();
    }

    private RouteResponse parseOneRoute(Map<String, String> fields) {
        String id = fields.get("id");
        String uri = fields.get("uri");
        int order = fields.containsKey("order") ? Integer.parseInt(fields.get("order")) : 0;
        return new RouteResponse(id, uri,
                parseDefs(fields, "predicates"),
                parseDefs(fields, "filters"),
                order);
    }

    private List<GatewayDefinition> parseDefs(Map<String, String> fields, String type) {
        Map<Integer, Map<String, String>> indexed = new TreeMap<>();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            Matcher m = DEF_PATTERN.matcher(e.getKey());
            if (!m.matches() || !m.group(1).equals(type)) continue;
            int idx = Integer.parseInt(m.group(2));
            String rem = m.group(3);
            indexed.computeIfAbsent(idx, k -> new LinkedHashMap<>())
                    .put(rem != null ? rem : "_shorthand_", e.getValue());
        }

        List<GatewayDefinition> result = new ArrayList<>();
        for (Map<String, String> df : indexed.values()) {
            if (df.containsKey("_shorthand_")) {
                result.add(GatewayDefinition.fromShorthand(df.get("_shorthand_")));
            } else {
                String name = df.getOrDefault("name", "");
                Map<String, String> args = new LinkedHashMap<>();
                df.forEach((k, v) -> { if (k.startsWith("args.")) args.put(k.substring(5), v); });
                result.add(new GatewayDefinition(name, args));
            }
        }
        return result;
    }

    // ── Helpers ─────────────────────────────────────────────────

    private Mono<Integer> findRouteIndexOrError(String routeId, String app, String profile, String label) {
        return findRouteIndex(routeId, app, profile, label)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Route not found: " + routeId)));
    }

    private Mono<Integer> findRouteIndex(String routeId, String app, String profile, String label) {
        return repository.findByKeyPattern(app, profile, label, ROUTE_PREFIX + "[%].id")
                .filter(p -> routeId.equals(p.getPropValue()))
                .next()
                .map(p -> extractRouteIndex(p.getId().propKey()))
                .filter(i -> i >= 0);
    }

    private Mono<Integer> nextRouteIndex(String app, String profile, String label) {
        return repository.findByKeyPattern(app, profile, label, ROUTE_PREFIX + "[%")
                .map(p -> extractRouteIndex(p.getId().propKey()))
                .filter(i -> i >= 0)
                .reduce(-1, Integer::max)
                .map(max -> max + 1);
    }

    // ── Swagger URL registration ────────────────────────────────

    private boolean shouldRegisterSwagger(RouteRequest request) {
        return request.registerSwagger() == null || request.registerSwagger();
    }

    private RouteRequest buildApiDocsRoute(String baseId, String uri) {
        String docsId = baseId + API_DOCS_SUFFIX;
        Map<String, String> pathArg = new LinkedHashMap<>();
        pathArg.put("_genkey_0", "/v3/api-docs/" + baseId);
        GatewayDefinition pathPredicate = new GatewayDefinition("Path", pathArg);

        Map<String, String> rewriteArgs = new LinkedHashMap<>();
        rewriteArgs.put("_genkey_0", "/v3/api-docs/" + baseId);
        rewriteArgs.put("_genkey_1", "/v3/api-docs");
        GatewayDefinition rewriteFilter = new GatewayDefinition("RewritePath", rewriteArgs);

        return new RouteRequest(
                docsId, uri,
                List.of(pathPredicate),
                List.of(rewriteFilter),
                null,
                Boolean.FALSE);
    }

    private Mono<Void> addSwaggerUrlEntry(String app, String profile, String label, String name) {
        return readSwaggerUrls(app, profile, label).flatMap(existing -> {
            existing.removeIf(e -> name.equals(e.get("name")));
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("name", name);
            entry.put("url", "/v3/api-docs/" + name);
            existing.add(entry);
            return writeSwaggerUrls(app, profile, label, existing);
        });
    }

    private Mono<Void> removeSwaggerUrlEntry(String app, String profile, String label, String name) {
        return readSwaggerUrls(app, profile, label).flatMap(existing -> {
            boolean removed = existing.removeIf(e -> name.equals(e.get("name")));
            if (!removed) return Mono.empty();
            return writeSwaggerUrls(app, profile, label, existing);
        });
    }

    private Mono<List<Map<String, String>>> readSwaggerUrls(String app, String profile, String label) {
        return repository.findByKeyPattern(app, profile, label, SWAGGER_URLS_PREFIX + "[%")
                .collectList()
                .map(props -> {
                    Map<Integer, Map<String, String>> indexed = new TreeMap<>();
                    for (ConfigProperty prop : props) {
                        Matcher m = SWAGGER_URL_INDEX_PATTERN.matcher(prop.getId().propKey());
                        if (!m.matches()) continue;
                        int idx = Integer.parseInt(m.group(1));
                        String field = m.group(2);
                        indexed.computeIfAbsent(idx, k -> new LinkedHashMap<>())
                                .put(field, prop.getPropValue());
                    }
                    return new ArrayList<>(indexed.values());
                });
    }

    private Mono<Void> writeSwaggerUrls(String app, String profile, String label,
                                        List<Map<String, String>> entries) {
        return repository.deleteByKeyPattern(app, profile, label, SWAGGER_URLS_PREFIX + "[%")
                .then(Mono.defer(() -> {
                    Map<String, String> kvs = new LinkedHashMap<>();
                    for (int i = 0; i < entries.size(); i++) {
                        Map<String, String> e = entries.get(i);
                        if (e.get("name") != null) {
                            kvs.put(SWAGGER_URLS_PREFIX + "[" + i + "].name", e.get("name"));
                        }
                        if (e.get("url") != null) {
                            kvs.put(SWAGGER_URLS_PREFIX + "[" + i + "].url", e.get("url"));
                        }
                    }
                    return saveAll(app, profile, label, kvs);
                }));
    }

    private int extractRouteIndex(String propKey) {
        Matcher m = ROUTE_INDEX_PATTERN.matcher(propKey);
        return m.matches() ? Integer.parseInt(m.group(1)) : -1;
    }

    private RouteResponse toRouteResponse(RouteRequest req) {
        return new RouteResponse(req.id(), req.uri(),
                nullSafe(req.predicates()), nullSafe(req.filters()),
                req.order() != null ? req.order() : 0);
    }

    private ConfigPropertyId toId(PropertyRequest r) {
        return new ConfigPropertyId(r.application(), r.profile(), r.label(), r.propKey());
    }

    private PropertyResponse toPropertyResponse(ConfigProperty entity) {
        ConfigPropertyId id = entity.getId();
        return new PropertyResponse(id.application(), id.profile(),
                id.label(), id.propKey(), entity.getPropValue());
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return Objects.requireNonNullElse(list, List.of());
    }
}
