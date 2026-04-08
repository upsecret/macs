package com.macs.authserver.service;

import com.macs.authserver.domain.GroupInfo;
import com.macs.authserver.domain.GroupMember;
import com.macs.authserver.domain.GroupResource;
import com.macs.authserver.domain.SystemConnector;
import com.macs.authserver.domain.UserResource;
import com.macs.authserver.dto.GroupRequest;
import com.macs.authserver.repository.GroupInfoRepository;
import com.macs.authserver.repository.GroupMemberRepository;
import com.macs.authserver.repository.GroupResourceRepository;
import com.macs.authserver.repository.SystemConnectorRepository;
import com.macs.authserver.repository.UserResourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class AuthManagementService {

    private final SystemConnectorRepository systemConnectorRepo;
    private final GroupInfoRepository groupInfoRepo;
    private final GroupMemberRepository groupMemberRepo;
    private final GroupResourceRepository groupResourceRepo;
    private final UserResourceRepository userResourceRepo;

    public AuthManagementService(SystemConnectorRepository systemConnectorRepo,
                                 GroupInfoRepository groupInfoRepo,
                                 GroupMemberRepository groupMemberRepo,
                                 GroupResourceRepository groupResourceRepo,
                                 UserResourceRepository userResourceRepo) {
        this.systemConnectorRepo = systemConnectorRepo;
        this.groupInfoRepo = groupInfoRepo;
        this.groupMemberRepo = groupMemberRepo;
        this.groupResourceRepo = groupResourceRepo;
        this.userResourceRepo = userResourceRepo;
    }

    // ── Systems ─────────────────────────────────────────────
    public Flux<SystemConnector> findAllSystemConnectors() { return systemConnectorRepo.findAll(); }
    public Flux<String> findDistinctSystems() { return systemConnectorRepo.findDistinctSystems(); }
    public Flux<SystemConnector> findConnectorsBySystem(String systemName) { return systemConnectorRepo.findBySystemName(systemName); }

    // ── Groups ──────────────────────────────────────────────
    public Flux<GroupInfo> findGroupsBySystem(String systemName) { return groupInfoRepo.findBySystemName(systemName); }

    public Mono<GroupInfo> createGroup(String systemName, GroupRequest request) {
        return groupInfoRepo.save(new GroupInfo(systemName, request.groupName()));
    }

    public Mono<GroupInfo> updateGroup(Long groupId, GroupRequest request) {
        return groupInfoRepo.findById(groupId)
                .switchIfEmpty(notFound("Group not found: " + groupId))
                .flatMap(g -> { g.setGroupName(request.groupName()); return groupInfoRepo.save(g); });
    }

    // ── Members ─────────────────────────────────────────────
    public Flux<GroupMember> findMembers(Long groupId) { return groupMemberRepo.findByGroupId(groupId); }
    public Mono<Void> addMember(Long groupId, String empNo) { return groupMemberRepo.insert(groupId, empNo); }
    public Mono<Void> removeMember(Long groupId, String empNo) { return groupMemberRepo.delete(groupId, empNo); }

    // ── Group Resources ─────────────────────────────────────
    public Flux<GroupResource> findGroupResources(Long groupId) { return groupResourceRepo.findByGroupId(groupId); }
    public Mono<Void> addGroupResource(Long groupId, String res) { return groupResourceRepo.insert(groupId, res); }
    public Mono<Void> removeGroupResource(Long groupId, String res) { return groupResourceRepo.delete(groupId, res); }

    // ── User Resources ──────────────────────────────────────
    public Flux<UserResource> findUserResources(String empNo, String sys) { return userResourceRepo.findByEmployeeNumberAndSystemName(empNo, sys); }
    public Mono<Void> addUserResource(String empNo, String sys, String res) { return userResourceRepo.insert(empNo, sys, res); }
    public Mono<Void> removeUserResource(String empNo, String sys, String res) { return userResourceRepo.delete(empNo, sys, res); }

    private static <T> Mono<T> notFound(String msg) { return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, msg)); }
}
