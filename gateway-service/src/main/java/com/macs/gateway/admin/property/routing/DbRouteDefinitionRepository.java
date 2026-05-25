package com.macs.gateway.admin.property.routing;

import com.macs.gateway.admin.property.dto.GatewayDefinition;
import com.macs.gateway.admin.property.dto.RouteRequest;
import com.macs.gateway.admin.property.dto.RouteResponse;
import com.macs.gateway.admin.property.service.ConfigPropertyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * RouteDefinitionRepository backed by the PROPERTIES table (same storage admin-server's
 * Config Server JDBC backend used to read from). After the admin merge, routes are
 * loaded directly in-process — Spring Cloud Gateway's CompositeRouteDefinitionLocator
 * picks up this repository alongside its built-in PropertiesRouteDefinitionLocator
 * (which is now empty since spring.config.import is gone).
 */
@Component
public class DbRouteDefinitionRepository implements RouteDefinitionRepository {

    private static final Logger log = LoggerFactory.getLogger(DbRouteDefinitionRepository.class);

    private static final String GATEWAY_APP = "gateway-service";
    private static final String GATEWAY_PROFILE = "default";
    private static final String GATEWAY_LABEL = "main";

    private final ConfigPropertyService configPropertyService;

    public DbRouteDefinitionRepository(ConfigPropertyService configPropertyService) {
        this.configPropertyService = configPropertyService;
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return configPropertyService
                .findRoutes(GATEWAY_APP, GATEWAY_PROFILE, GATEWAY_LABEL)
                .flatMapMany(Flux::fromIterable)
                .map(DbRouteDefinitionRepository::toDefinition)
                .doOnComplete(() -> log.debug("Loaded route definitions from DB"));
    }

    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        return route.flatMap(def -> {
            RouteRequest request = toRequest(def);
            // Upsert semantics: update if route id exists, else create.
            return configPropertyService.findRoutes(GATEWAY_APP, GATEWAY_PROFILE, GATEWAY_LABEL)
                    .flatMap(routes -> {
                        boolean exists = routes.stream().anyMatch(r -> def.getId().equals(r.id()));
                        if (exists) {
                            return configPropertyService.updateRoute(
                                    def.getId(), GATEWAY_APP, GATEWAY_PROFILE, GATEWAY_LABEL, request);
                        }
                        return configPropertyService.createRoute(
                                GATEWAY_APP, GATEWAY_PROFILE, GATEWAY_LABEL, request);
                    })
                    .then();
        });
    }

    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return routeId.flatMap(id -> configPropertyService.deleteRoute(
                id, GATEWAY_APP, GATEWAY_PROFILE, GATEWAY_LABEL));
    }

    static RouteDefinition toDefinition(RouteResponse r) {
        RouteDefinition def = new RouteDefinition();
        def.setId(r.id());
        def.setUri(URI.create(r.uri()));
        def.setOrder(r.order());
        if (r.predicates() != null) {
            for (GatewayDefinition gd : r.predicates()) {
                PredicateDefinition pd = new PredicateDefinition();
                pd.setName(gd.name());
                pd.setArgs(new LinkedHashMap<>(gd.args()));
                def.getPredicates().add(pd);
            }
        }
        if (r.filters() != null) {
            for (GatewayDefinition gd : r.filters()) {
                FilterDefinition fd = new FilterDefinition();
                fd.setName(gd.name());
                fd.setArgs(new LinkedHashMap<>(gd.args()));
                def.getFilters().add(fd);
            }
        }
        return def;
    }

    static RouteRequest toRequest(RouteDefinition def) {
        List<GatewayDefinition> preds = def.getPredicates().stream()
                .map(p -> new GatewayDefinition(p.getName(), new LinkedHashMap<>(p.getArgs())))
                .toList();
        List<GatewayDefinition> filters = def.getFilters().stream()
                .map(f -> new GatewayDefinition(f.getName(), new LinkedHashMap<>(f.getArgs())))
                .toList();
        return new RouteRequest(
                def.getId(),
                def.getUri().toString(),
                preds,
                filters,
                def.getOrder(),
                Boolean.FALSE);
    }
}
