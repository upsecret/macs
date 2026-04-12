package com.macs.adminserver.permission.controller;

import com.macs.adminserver.permission.dto.PermissionRequest;
import com.macs.adminserver.permission.dto.PermissionResponse;
import com.macs.adminserver.permission.dto.UserPermissionsResponse;
import com.macs.adminserver.permission.service.PermissionService;
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
@RequestMapping("/api/admin/permissions")
@Tag(name = "Permission", description = "User-to-route access grants")
public class PermissionController {

    private final PermissionService service;

    public PermissionController(PermissionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List permissions (optionally filtered by user)")
    public List<PermissionResponse> list(
            @RequestParam(required = false) String appName,
            @RequestParam(required = false) String employeeNumber) {
        return service.list(appName, employeeNumber);
    }

    @GetMapping("/users/{appName}/{employeeNumber}")
    @Operation(summary = "Fetch permissions for a specific user — used by auth-server")
    public UserPermissionsResponse forUser(
            @PathVariable String appName,
            @PathVariable String employeeNumber) {
        return service.forUser(appName, employeeNumber);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Grant a new permission")
    public PermissionResponse grant(@RequestBody PermissionRequest request) {
        return service.grant(request);
    }

    @PutMapping
    @Operation(summary = "Update role of an existing permission")
    public PermissionResponse updateRole(@RequestBody PermissionRequest request) {
        return service.updateRole(request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a permission")
    public void revoke(
            @RequestParam String appName,
            @RequestParam String employeeNumber,
            @RequestParam String system,
            @RequestParam String connector) {
        service.revoke(appName, employeeNumber, system, connector);
    }
}
