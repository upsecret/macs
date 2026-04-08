package com.macs.authserver.controller;

import com.macs.authserver.domain.GroupInfo;
import com.macs.authserver.domain.GroupMember;
import com.macs.authserver.domain.GroupResource;
import com.macs.authserver.domain.SystemConnector;
import com.macs.authserver.domain.UserResource;
import com.macs.authserver.dto.GroupRequest;
import com.macs.authserver.dto.MemberRequest;
import com.macs.authserver.dto.ResourceRequest;
import com.macs.authserver.service.AuthManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Access Management", description = "Systems, groups, members, and resource management")
public class AuthManagementController {

    private final AuthManagementService service;

    public AuthManagementController(AuthManagementService service) {
        this.service = service;
    }

    // ── Systems ─────────────────────────────────────────────

    @GetMapping("/systems")
    @Operation(summary = "List all system-connector mappings")
    public Flux<SystemConnector> getSystems() {
        return service.findAllSystemConnectors();
    }

    @GetMapping("/systems/{systemName}/connectors")
    @Operation(summary = "List connectors for a system")
    public Flux<SystemConnector> getConnectors(@PathVariable String systemName) {
        return service.findConnectorsBySystem(systemName);
    }

    // ── Groups ──────────────────────────────────────────────

    @GetMapping("/systems/{systemName}/groups")
    @Operation(summary = "List groups for a system")
    public Flux<GroupInfo> getGroups(@PathVariable String systemName) {
        return service.findGroupsBySystem(systemName);
    }

    @PostMapping("/systems/{systemName}/groups")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new group")
    public Mono<GroupInfo> createGroup(@PathVariable String systemName, @RequestBody GroupRequest request) {
        return service.createGroup(systemName, request);
    }

    @PutMapping("/groups/{groupId}")
    @Operation(summary = "Update a group")
    public Mono<GroupInfo> updateGroup(@PathVariable Long groupId, @RequestBody GroupRequest request) {
        return service.updateGroup(groupId, request);
    }

    // ── Members ─────────────────────────────────────────────

    @GetMapping("/groups/{groupId}/members")
    @Operation(summary = "List members of a group")
    public Flux<GroupMember> getMembers(@PathVariable Long groupId) {
        return service.findMembers(groupId);
    }

    @PostMapping("/groups/{groupId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Void> addMember(@PathVariable Long groupId, @RequestBody MemberRequest request) {
        return service.addMember(groupId, request.employeeNumber());
    }

    @DeleteMapping("/groups/{groupId}/members/{employeeNumber}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> removeMember(@PathVariable Long groupId, @PathVariable String employeeNumber) {
        return service.removeMember(groupId, employeeNumber);
    }

    // ── Group Resources ─────────────────────────────────────

    @GetMapping("/groups/{groupId}/resources")
    @Operation(summary = "List resources (connectors) assigned to a group")
    public Flux<GroupResource> getGroupResources(@PathVariable Long groupId) {
        return service.findGroupResources(groupId);
    }

    @PostMapping("/groups/{groupId}/resources")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Void> addGroupResource(@PathVariable Long groupId, @RequestBody ResourceRequest request) {
        return service.addGroupResource(groupId, request.resourceName());
    }

    @DeleteMapping("/groups/{groupId}/resources")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> removeGroupResource(@PathVariable Long groupId, @RequestParam String resourceName) {
        return service.removeGroupResource(groupId, resourceName);
    }

    // ── User Resources ──────────────────────────────────────

    @GetMapping("/users/{employeeNumber}/systems/{systemName}/resources")
    public Flux<UserResource> getUserResources(@PathVariable String employeeNumber, @PathVariable String systemName) {
        return service.findUserResources(employeeNumber, systemName);
    }

    @PostMapping("/users/{employeeNumber}/systems/{systemName}/resources")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Void> addUserResource(@PathVariable String employeeNumber, @PathVariable String systemName, @RequestBody ResourceRequest request) {
        return service.addUserResource(employeeNumber, systemName, request.resourceName());
    }

    @DeleteMapping("/users/{employeeNumber}/systems/{systemName}/resources")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> removeUserResource(@PathVariable String employeeNumber, @PathVariable String systemName, @RequestParam String resourceName) {
        return service.removeUserResource(employeeNumber, systemName, resourceName);
    }
}
