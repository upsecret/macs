package com.macs.gateway.admin.property.controller;

import com.macs.gateway.admin.property.dto.PropertyRequest;
import com.macs.gateway.admin.property.dto.PropertyResponse;
import com.macs.gateway.admin.property.service.ConfigPropertyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/config/properties")
@Tag(name = "Config Properties", description = "CRUD operations for config properties")
public class ConfigPropertiesController {

    private final ConfigPropertyService service;

    public ConfigPropertiesController(ConfigPropertyService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Query properties by application and profile")
    public Flux<PropertyResponse> getProperties(
            @RequestParam String application,
            @RequestParam(defaultValue = "default") String profile,
            @RequestParam(defaultValue = "main") String label) {
        return service.findProperties(application, profile, label);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new property")
    public Mono<PropertyResponse> createProperty(@RequestBody PropertyRequest request) {
        return service.createProperty(request);
    }

    @PutMapping
    @Operation(summary = "Update an existing property value")
    public Mono<PropertyResponse> updateProperty(@RequestBody PropertyRequest request) {
        return service.updateProperty(request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a property")
    public Mono<Void> deleteProperty(
            @RequestParam String application,
            @RequestParam(defaultValue = "default") String profile,
            @RequestParam(defaultValue = "main") String label,
            @RequestParam String propKey) {
        return service.deleteProperty(application, profile, label, propKey);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Publish Spring Cloud Bus refresh event to all services")
    public Map<String, String> refresh() {
        service.publishRefreshEvent();
        return Map.of("status", "refresh event published");
    }
}
