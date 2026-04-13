package com.macs.adminserver.property.service;

import com.macs.adminserver.property.domain.ConfigProperty;
import com.macs.adminserver.property.domain.ConfigPropertyId;
import com.macs.adminserver.property.dto.GatewayDefinition;
import com.macs.adminserver.property.dto.PropertyRequest;
import com.macs.adminserver.property.dto.PropertyResponse;
import com.macs.adminserver.property.dto.RouteRequest;
import com.macs.adminserver.property.dto.RouteResponse;
import com.macs.adminserver.property.repository.ConfigPropertyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.bus.BusProperties;
import org.springframework.cloud.bus.event.RefreshRemoteApplicationEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class ConfigPropertyService {

    private static final Logger log = LoggerFactory.getLogger(ConfigPropertyService.class);

    // Spring Cloud Gateway 4.x: prefix moved to spring.cloud.gateway.server.webflux.*
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

    public List<PropertyResponse> findProperties(String application, String profile, String label) {
        return repository.findByIdApplicationAndIdProfileAndIdLabel(application, profile, label)
                .stream()
                .map(this::toPropertyResponse)
                .toList();
    }

    @Transactional
    public PropertyResponse createProperty(PropertyRequest request) {
        ConfigPropertyId id = toId(request);
        if (repository.existsById(id)) {
            log.warn("Property CREATE conflict app={} profile={} label={} key={}",
                    request.application(), request.profile(), request.label(), request.propKey());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Property already exists");
        }
        PropertyResponse saved = toPropertyResponse(
                repository.save(new ConfigProperty(id, request.propValue())));
        log.info("Property CREATED app={} profile={} label={} key={}",
                request.application(), request.profile(), request.label(), request.propKey());
        return saved;
    }

    @Transactional
    public PropertyResponse updateProperty(PropertyRequest request) {
        ConfigPropertyId id = toId(request);
        ConfigProperty entity = repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Property UPDATE not-found app={} profile={} label={} key={}",
                            request.application(), request.profile(), request.label(), request.propKey());
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found");
                });
        entity.setPropValue(request.propValue());
        PropertyResponse saved = toPropertyResponse(repository.save(entity));
        log.info("Property UPDATED app={} profile={} label={} key={}",
                request.application(), request.profile(), request.label(), request.propKey());
        return saved;
    }

    @Transactional
    public void deleteProperty(String application, String profile, String label, String propKey) {
        ConfigPropertyId id = new ConfigPropertyId(application, profile, label, propKey);
        if (!repository.existsById(id)) {
            log.warn("Property DELETE not-found app={} profile={} label={} key={}",
                    application, profile, label, propKey);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found");
        }
        repository.deleteById(id);
        log.info("Property DELETED app={} profile={} label={} key={}",
                application, profile, label, propKey);
    }

    // ── Route CRUD ──────────────────────────────────────────────

    public List<RouteResponse> findRoutes(String application, String profile, String label) {
        List<ConfigProperty> props = repository.findByKeyPattern(
                application, profile, label, ROUTE_PREFIX + "[%");
        return parseRoutes(props);
    }

    @Transactional
    public RouteResponse createRoute(String application, String profile, String label,
                                     RouteRequest request) {
        int nextIndex = nextRouteIndex(application, profile, label);
        saveRouteProperties(application, profile, label, nextIndex, request);

        boolean swagger = shouldRegisterSwagger(request);
        if (swagger) {
            int docsIndex = nextRouteIndex(application, profile, label);
            saveRouteProperties(application, profile, label, docsIndex,
                    buildApiDocsRoute(request.id(), request.uri()));
            addSwaggerUrlEntry(application, profile, label, request.id());
        }
        log.info("Route CREATED app={} profile={} label={} id={} uri={} index={} swagger={}",
                application, profile, label, request.id(), request.uri(), nextIndex, swagger);
        return toRouteResponse(request);
    }

    @Transactional
    public RouteResponse updateRoute(String routeId, String application, String profile,
                                     String label, RouteRequest request) {
        int index = findRouteIndexOrThrow(routeId, application, profile, label);
        repository.deleteByKeyPattern(application, profile, label,
                ROUTE_PREFIX + "[" + index + "].%");
        saveRouteProperties(application, profile, label, index, request);

        // sync companion api-docs route uri (if it exists)
        String docsId = routeId + API_DOCS_SUFFIX;
        Integer docsIdx = findRouteIndex(docsId, application, profile, label);
        if (docsIdx != null) {
            repository.deleteByKeyPattern(application, profile, label,
                    ROUTE_PREFIX + "[" + docsIdx + "].%");
            saveRouteProperties(application, profile, label, docsIdx,
                    buildApiDocsRoute(routeId, request.uri()));
        }
        log.info("Route UPDATED app={} profile={} label={} id={} newUri={} docsSynced={}",
                application, profile, label, routeId, request.uri(), docsIdx != null);
        return toRouteResponse(request);
    }

    @Transactional
    public void deleteRoute(String routeId, String application, String profile, String label) {
        int index = findRouteIndexOrThrow(routeId, application, profile, label);
        repository.deleteByKeyPattern(application, profile, label,
                ROUTE_PREFIX + "[" + index + "].%");

        // also delete companion api-docs route + swagger url entry
        String docsId = routeId + API_DOCS_SUFFIX;
        Integer docsIdx = findRouteIndex(docsId, application, profile, label);
        if (docsIdx != null) {
            repository.deleteByKeyPattern(application, profile, label,
                    ROUTE_PREFIX + "[" + docsIdx + "].%");
        }
        removeSwaggerUrlEntry(application, profile, label, routeId);
        log.info("Route DELETED app={} profile={} label={} id={} docsRemoved={}",
                application, profile, label, routeId, docsIdx != null);
    }

    // ── Refresh ─────────────────────────────────────────────────

    public void publishRefreshEvent() {
        // originService 는 BusProperties.getId() 를 써야 한다. applicationContext.getId() 로 만들면
        // Spring Cloud Bus 의 acceptLocal 핸들러가 isFromSelf 검사에서 false 로 떨어져
        // 이벤트가 outbound channel 로 안 흘러가고 다른 인스턴스에 도착하지 못한다.
        // 표준 /actuator/busrefresh 엔드포인트도 동일하게 BusProperties.getId() 를 쓴다.
        String origin = busProperties.getId();
        log.info("Publishing RefreshRemoteApplicationEvent destination=** origin={}", origin);
        applicationContext.publishEvent(
                new RefreshRemoteApplicationEvent(this, origin, "**"));
    }

    // ════════════════════════════════════════════════════════════
    //  Route 저장 — shorthand 가능하면 한 줄, 아니면 name+args 구조
    // ════════════════════════════════════════════════════════════

    private void saveRouteProperties(String app, String profile, String label,
                                     int index, RouteRequest request) {
        String p = ROUTE_PREFIX + "[" + index + "].";

        save(app, profile, label, p + "id", request.id());
        save(app, profile, label, p + "uri", request.uri());
        if (request.order() != null) {
            save(app, profile, label, p + "order", String.valueOf(request.order()));
        }

        saveDefs(app, profile, label, p + "predicates", nullSafe(request.predicates()));
        saveDefs(app, profile, label, p + "filters", nullSafe(request.filters()));
    }

    private void saveDefs(String app, String profile, String label,
                          String prefix, List<GatewayDefinition> defs) {
        for (int i = 0; i < defs.size(); i++) {
            GatewayDefinition def = defs.get(i);
            String dp = prefix + "[" + i + "]";

            String shorthand = def.toShorthand();
            if (shorthand != null) {
                save(app, profile, label, dp, shorthand);
            } else {
                save(app, profile, label, dp + ".name", def.name());
                for (Map.Entry<String, String> arg : def.args().entrySet()) {
                    save(app, profile, label, dp + ".args." + arg.getKey(), arg.getValue());
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    //  Route 파싱 — shorthand + structured 모두 지원
    // ════════════════════════════════════════════════════════════

    private List<RouteResponse> parseRoutes(List<ConfigProperty> props) {
        Map<Integer, Map<String, String>> routeMap = new TreeMap<>();
        for (ConfigProperty prop : props) {
            int idx = extractRouteIndex(prop.getId().getPropKey());
            if (idx >= 0) {
                String field = prop.getId().getPropKey()
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

    private int findRouteIndexOrThrow(String routeId, String app, String profile, String label) {
        Integer idx = findRouteIndex(routeId, app, profile, label);
        if (idx == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found: " + routeId);
        }
        return idx;
    }

    private Integer findRouteIndex(String routeId, String app, String profile, String label) {
        List<ConfigProperty> idProps = repository.findByKeyPattern(
                app, profile, label, ROUTE_PREFIX + "[%].id");
        return idProps.stream()
                .filter(p -> routeId.equals(p.getPropValue()))
                .map(p -> extractRouteIndex(p.getId().getPropKey()))
                .findFirst()
                .orElse(null);
    }

    private int nextRouteIndex(String app, String profile, String label) {
        List<ConfigProperty> existing = repository.findByKeyPattern(
                app, profile, label, ROUTE_PREFIX + "[%");
        return existing.stream()
                .map(p -> extractRouteIndex(p.getId().getPropKey()))
                .filter(i -> i >= 0)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
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

    private void addSwaggerUrlEntry(String app, String profile, String label, String name) {
        List<Map<String, String>> existing = readSwaggerUrls(app, profile, label);
        // remove any existing entry with the same name (defensive)
        existing.removeIf(e -> name.equals(e.get("name")));
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("name", name);
        entry.put("url", "/v3/api-docs/" + name);
        existing.add(entry);
        writeSwaggerUrls(app, profile, label, existing);
    }

    private void removeSwaggerUrlEntry(String app, String profile, String label, String name) {
        List<Map<String, String>> existing = readSwaggerUrls(app, profile, label);
        boolean removed = existing.removeIf(e -> name.equals(e.get("name")));
        if (removed) {
            writeSwaggerUrls(app, profile, label, existing);
        }
    }

    private List<Map<String, String>> readSwaggerUrls(String app, String profile, String label) {
        List<ConfigProperty> props = repository.findByKeyPattern(
                app, profile, label, SWAGGER_URLS_PREFIX + "[%");
        Map<Integer, Map<String, String>> indexed = new TreeMap<>();
        for (ConfigProperty prop : props) {
            Matcher m = SWAGGER_URL_INDEX_PATTERN.matcher(prop.getId().getPropKey());
            if (!m.matches()) continue;
            int idx = Integer.parseInt(m.group(1));
            String field = m.group(2);
            indexed.computeIfAbsent(idx, k -> new LinkedHashMap<>())
                    .put(field, prop.getPropValue());
        }
        return new ArrayList<>(indexed.values());
    }

    private void writeSwaggerUrls(String app, String profile, String label,
                                  List<Map<String, String>> entries) {
        repository.deleteByKeyPattern(app, profile, label, SWAGGER_URLS_PREFIX + "[%");
        for (int i = 0; i < entries.size(); i++) {
            Map<String, String> e = entries.get(i);
            if (e.get("name") != null) {
                save(app, profile, label, SWAGGER_URLS_PREFIX + "[" + i + "].name", e.get("name"));
            }
            if (e.get("url") != null) {
                save(app, profile, label, SWAGGER_URLS_PREFIX + "[" + i + "].url", e.get("url"));
            }
        }
    }

    private int extractRouteIndex(String propKey) {
        Matcher m = ROUTE_INDEX_PATTERN.matcher(propKey);
        return m.matches() ? Integer.parseInt(m.group(1)) : -1;
    }

    private void save(String app, String profile, String label, String key, String value) {
        repository.save(new ConfigProperty(new ConfigPropertyId(app, profile, label, key), value));
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
        return new PropertyResponse(id.getApplication(), id.getProfile(),
                id.getLabel(), id.getPropKey(), entity.getPropValue());
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return Objects.requireNonNullElse(list, List.of());
    }
}
