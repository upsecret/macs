package com.macs.gateway.admin.connector.controller;

import com.macs.gateway.admin.connector.dto.AvailableRouteResponse;
import com.macs.gateway.admin.connector.dto.ConnectorRequest;
import com.macs.gateway.admin.connector.dto.ConnectorResponse;
import com.macs.gateway.admin.connector.dto.RouteMetadataResponse;
import com.macs.gateway.admin.connector.service.ConnectorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/connectors")
@Tag(name = "Connector", description = "Connector metadata on top of gateway routes")
public class ConnectorController {

    private final ConnectorService service;

    public ConnectorController(ConnectorService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List connectors with derived active state")
    public Flux<ConnectorResponse> list() {
        return service.list();
    }

    @GetMapping("/available-routes")
    @Operation(summary = "List gateway routes not yet linked to a connector")
    public Flux<AvailableRouteResponse> availableRoutes() {
        return service.availableRoutes();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new connector for an existing gateway route")
    public Mono<ConnectorResponse> create(@RequestBody ConnectorRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update title/description/type of a connector")
    public Mono<ConnectorResponse> update(@PathVariable String id, @RequestBody ConnectorRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a connector (gateway route is untouched)")
    public Mono<Void> delete(@PathVariable String id) {
        return service.delete(id);
    }

    @GetMapping("/{id}/route-metadata")
    @Operation(summary = "Gateway route metadata for path transformation")
    public Mono<RouteMetadataResponse> routeMetadata(@PathVariable String id) {
        return service.getRouteMetadata(id);
    }

    @GetMapping(value = "/{id}/api-docs", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Proxy-fetch the connector's OpenAPI JSON",
            description = "docsUrl 이 있으면 그 URL, 없으면 gateway /v3/api-docs/{id} 에서 JSON 을 가져와 그대로 리턴. CORS 회피 목적으로 gateway 가 프록시.")
    public Mono<ResponseEntity<String>> apiDocs(@PathVariable String id) {
        return service.fetchApiDocs(id)
                .map(body -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body));
    }
}
