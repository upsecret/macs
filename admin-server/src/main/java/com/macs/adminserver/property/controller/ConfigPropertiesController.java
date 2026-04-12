package com.macs.adminserver.property.controller;

import com.macs.adminserver.property.dto.PropertyRequest;
import com.macs.adminserver.property.dto.PropertyResponse;
import com.macs.adminserver.property.service.ConfigPropertyService;
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

import java.util.List;
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
    public List<PropertyResponse> getProperties(
            @RequestParam String application,
            @RequestParam(defaultValue = "default") String profile,
            @RequestParam(defaultValue = "main") String label) {
        return service.findProperties(application, profile, label);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new property")
    public PropertyResponse createProperty(@RequestBody PropertyRequest request) {
        return service.createProperty(request);
    }

    @PutMapping
    @Operation(summary = "Update an existing property value")
    public PropertyResponse updateProperty(@RequestBody PropertyRequest request) {
        return service.updateProperty(request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a property")
    public void deleteProperty(
            @RequestParam String application,
            @RequestParam(defaultValue = "default") String profile,
            @RequestParam(defaultValue = "main") String label,
            @RequestParam String propKey) {
        service.deleteProperty(application, profile, label, propKey);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Publish Spring Cloud Bus refresh event to all services")
    public Map<String, String> refresh() {
        service.publishRefreshEvent();
        return Map.of("status", "refresh event published");
    }
}
