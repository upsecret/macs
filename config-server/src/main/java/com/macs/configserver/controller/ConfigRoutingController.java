package com.macs.configserver.controller;

import com.macs.configserver.dto.RouteRequest;
import com.macs.configserver.dto.RouteResponse;
import com.macs.configserver.service.ConfigPropertyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/config/routes")
@Tag(name = "Config Routing", description = "Gateway routing configuration management")
public class ConfigRoutingController {

    private final ConfigPropertyService service;

    public ConfigRoutingController(ConfigPropertyService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Query gateway routes")
    public List<RouteResponse> getRoutes(
            @RequestParam(defaultValue = "gateway-service") String application,
            @RequestParam(defaultValue = "default") String profile,
            @RequestParam(defaultValue = "main") String label) {
        return service.findRoutes(application, profile, label);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new gateway route")
    public RouteResponse createRoute(
            @RequestParam(defaultValue = "gateway-service") String application,
            @RequestParam(defaultValue = "default") String profile,
            @RequestParam(defaultValue = "main") String label,
            @RequestBody RouteRequest request) {
        return service.createRoute(application, profile, label, request);
    }

    @PutMapping("/{routeId}")
    @Operation(summary = "Update an existing gateway route")
    public RouteResponse updateRoute(
            @PathVariable String routeId,
            @RequestParam(defaultValue = "gateway-service") String application,
            @RequestParam(defaultValue = "default") String profile,
            @RequestParam(defaultValue = "main") String label,
            @RequestBody RouteRequest request) {
        return service.updateRoute(routeId, application, profile, label, request);
    }

    @DeleteMapping("/{routeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a gateway route")
    public void deleteRoute(
            @PathVariable String routeId,
            @RequestParam(defaultValue = "gateway-service") String application,
            @RequestParam(defaultValue = "default") String profile,
            @RequestParam(defaultValue = "main") String label) {
        service.deleteRoute(routeId, application, profile, label);
    }
}
