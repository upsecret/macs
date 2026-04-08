package com.macs.authserver.controller;

import com.macs.authserver.domain.AppInfo;
import com.macs.authserver.domain.GroupInfo;
import com.macs.authserver.domain.GroupMember;
import com.macs.authserver.domain.GroupResource;
import com.macs.authserver.domain.UserResource;
import com.macs.authserver.dto.GroupRequest;
import com.macs.authserver.dto.MemberRequest;
import com.macs.authserver.dto.ResourceRequest;
import com.macs.authserver.service.AuthManagementService;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Access Management", description = "Apps, groups, members, and resource permission management")
public class AuthManagementController {

    private final AuthManagementService service;

    public AuthManagementController(AuthManagementService service) {
        this.service = service;
    }

    // ── Apps ────────────────────────────────────────────────────

    @GetMapping("/apps")
    @Operation(summary = "List all applications")
    public Flux<AppInfo> getApps() {
        return service.findAllApps();
    }

    // ── Groups ──────────────────────────────────────────────────

    @GetMapping("/apps/{appName}/groups")
    @Operation(summary = "List groups for an application")
    public Flux<GroupInfo> getGroups(@PathVariable String appName) {
        return service.findGroupsByAppName(appName);
    }

    @PostMapping("/apps/{appName}/groups")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new group")
    public Mono<GroupInfo> createGroup(
            @PathVariable String appName,
            @RequestBody GroupRequest request) {
        return service.createGroup(appName, request);
    }

    @PutMapping("/groups/{groupId}")
    @Operation(summary = "Update a group")
    public Mono<GroupInfo> updateGroup(
            @PathVariable String groupId,
            @RequestBody GroupRequest request) {
        return service.updateGroup(groupId, request);
    }

    // ── Group Members ───────────────────────────────────────────

    @GetMapping("/groups/{groupId}/members")
    @Operation(summary = "List members of a group")
    public Flux<GroupMember> getMembers(@PathVariable String groupId) {
        return service.findMembersByGroupId(groupId);
    }

    @PostMapping("/groups/{groupId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a member to a group")
    public Mono<Void> addMember(
            @PathVariable String groupId,
            @RequestBody MemberRequest request) {
        return service.addMember(groupId, request.employeeNumber());
    }

    @DeleteMapping("/groups/{groupId}/members/{employeeNumber}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a member from a group")
    public Mono<Void> removeMember(
            @PathVariable String groupId,
            @PathVariable String employeeNumber) {
        return service.removeMember(groupId, employeeNumber);
    }

    // ── Group Resources ─────────────────────────────────────────

    @GetMapping("/groups/{groupId}/resources")
    @Operation(summary = "List resources assigned to a group")
    public Flux<GroupResource> getGroupResources(@PathVariable String groupId) {
        return service.findGroupResources(groupId);
    }

    @PostMapping("/groups/{groupId}/resources")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a resource to a group")
    public Mono<Void> addGroupResource(
            @PathVariable String groupId,
            @RequestBody ResourceRequest request) {
        return service.addGroupResource(groupId, request.resourceName());
    }

    @DeleteMapping("/groups/{groupId}/resources")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a resource from a group")
    public Mono<Void> removeGroupResource(
            @PathVariable String groupId,
            @RequestParam String resourceName) {
        return service.removeGroupResource(groupId, resourceName);
    }

    // ── User Resources ──────────────────────────────────────────

    @GetMapping("/users/{employeeNumber}/apps/{appName}/resources")
    @Operation(summary = "List personal resources for a user in an app")
    public Flux<UserResource> getUserResources(
            @PathVariable String employeeNumber,
            @PathVariable String appName) {
        return service.findUserResources(employeeNumber, appName);
    }

    @PostMapping("/users/{employeeNumber}/apps/{appName}/resources")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a personal resource for a user")
    public Mono<Void> addUserResource(
            @PathVariable String employeeNumber,
            @PathVariable String appName,
            @RequestBody ResourceRequest request) {
        return service.addUserResource(employeeNumber, appName, request.resourceName());
    }

    @DeleteMapping("/users/{employeeNumber}/apps/{appName}/resources")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a personal resource from a user")
    public Mono<Void> removeUserResource(
            @PathVariable String employeeNumber,
            @PathVariable String appName,
            @RequestParam String resourceName) {
        return service.removeUserResource(employeeNumber, appName, resourceName);
    }
}
