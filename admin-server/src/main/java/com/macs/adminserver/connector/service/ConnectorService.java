package com.macs.adminserver.connector.service;

import com.macs.adminserver.connector.domain.Connector;
import com.macs.adminserver.connector.dto.AvailableRouteResponse;
import com.macs.adminserver.connector.dto.ConnectorRequest;
import com.macs.adminserver.connector.dto.ConnectorResponse;
import com.macs.adminserver.connector.repository.ConnectorRepository;
import com.macs.adminserver.property.dto.RouteResponse;
import com.macs.adminserver.property.service.ConfigPropertyService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ConnectorService {

    private static final String GATEWAY_APP = "gateway-service";
    private static final String GATEWAY_PROFILE = "default";
    private static final String GATEWAY_LABEL = "main";

    private static final Set<String> ALLOWED_TYPES = Set.of("agent", "api", "mcp");

    private final ConnectorRepository repository;
    private final ConfigPropertyService configPropertyService;

    public ConnectorService(ConnectorRepository repository,
                            ConfigPropertyService configPropertyService) {
        this.repository = repository;
        this.configPropertyService = configPropertyService;
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

        Connector saved = repository.save(new Connector(
                request.id(),
                request.title(),
                request.description(),
                request.type()));

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
        Connector saved = repository.save(entity);

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
