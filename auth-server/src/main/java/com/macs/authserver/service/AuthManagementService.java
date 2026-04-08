package com.macs.authserver.service;

import com.macs.authserver.domain.AppInfo;
import com.macs.authserver.domain.GroupInfo;
import com.macs.authserver.domain.GroupMember;
import com.macs.authserver.domain.GroupResource;
import com.macs.authserver.domain.UserResource;
import com.macs.authserver.dto.GroupRequest;
import com.macs.authserver.repository.AppInfoRepository;
import com.macs.authserver.repository.GroupInfoRepository;
import com.macs.authserver.repository.GroupMemberRepository;
import com.macs.authserver.repository.GroupResourceRepository;
import com.macs.authserver.repository.UserResourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class AuthManagementService {

    private final AppInfoRepository appInfoRepository;
    private final GroupInfoRepository groupInfoRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupResourceRepository groupResourceRepository;
    private final UserResourceRepository userResourceRepository;

    public AuthManagementService(AppInfoRepository appInfoRepository,
                                 GroupInfoRepository groupInfoRepository,
                                 GroupMemberRepository groupMemberRepository,
                                 GroupResourceRepository groupResourceRepository,
                                 UserResourceRepository userResourceRepository) {
        this.appInfoRepository = appInfoRepository;
        this.groupInfoRepository = groupInfoRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupResourceRepository = groupResourceRepository;
        this.userResourceRepository = userResourceRepository;
    }

    // ── Apps ────────────────────────────────────────────────────

    public Flux<AppInfo> findAllApps() {
        return appInfoRepository.findAll();
    }

    // ── Groups ──────────────────────────────────────────────────

    public Flux<GroupInfo> findGroupsByAppName(String appName) {
        return findAppByName(appName)
                .flatMapMany(app -> groupInfoRepository.findByAppId(app.getAppId()));
    }

    public Mono<GroupInfo> createGroup(String appName, GroupRequest request) {
        return findAppByName(appName)
                .flatMap(app -> {
                    String groupId = appName + "-" + request.groupName();
                    GroupInfo group = new GroupInfo(groupId, app.getAppId(), request.groupName());
                    return groupInfoRepository.save(group);
                });
    }

    public Mono<GroupInfo> updateGroup(String groupId, GroupRequest request) {
        return groupInfoRepository.findById(groupId)
                .switchIfEmpty(notFound("Group not found: " + groupId))
                .flatMap(group -> {
                    group.setGroupName(request.groupName());
                    return groupInfoRepository.save(group);
                });
    }

    // ── Group Members ───────────────────────────────────────────

    public Flux<GroupMember> findMembersByGroupId(String groupId) {
        return groupMemberRepository.findByGroupId(groupId);
    }

    public Mono<Void> addMember(String groupId, String employeeNumber) {
        return groupMemberRepository.insert(groupId, employeeNumber);
    }

    public Mono<Void> removeMember(String groupId, String employeeNumber) {
        return groupMemberRepository.deleteByGroupIdAndEmployeeNumber(groupId, employeeNumber);
    }

    // ── Group Resources ─────────────────────────────────────────

    public Flux<GroupResource> findGroupResources(String groupId) {
        return groupResourceRepository.findByGroupId(groupId);
    }

    public Mono<Void> addGroupResource(String groupId, String resourceName) {
        return groupResourceRepository.insert(groupId, resourceName);
    }

    public Mono<Void> removeGroupResource(String groupId, String resourceName) {
        return groupResourceRepository.deleteByGroupIdAndResourceName(groupId, resourceName);
    }

    // ── User Resources ──────────────────────────────────────────

    public Flux<UserResource> findUserResources(String employeeNumber, String appName) {
        return findAppByName(appName)
                .flatMapMany(app ->
                        userResourceRepository.findByEmployeeNumberAndAppId(
                                employeeNumber, app.getAppId()));
    }

    public Mono<Void> addUserResource(String employeeNumber, String appName, String resourceName) {
        return findAppByName(appName)
                .flatMap(app ->
                        userResourceRepository.insert(employeeNumber, app.getAppId(), resourceName));
    }

    public Mono<Void> removeUserResource(String employeeNumber, String appName, String resourceName) {
        return findAppByName(appName)
                .flatMap(app ->
                        userResourceRepository.deleteByEmployeeNumberAndAppIdAndResourceName(
                                employeeNumber, app.getAppId(), resourceName));
    }

    // ── Helpers ─────────────────────────────────────────────────

    private Mono<AppInfo> findAppByName(String appName) {
        return appInfoRepository.findByAppName(appName)
                .switchIfEmpty(notFound("App not found: " + appName));
    }

    private static <T> Mono<T> notFound(String message) {
        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, message));
    }
}
