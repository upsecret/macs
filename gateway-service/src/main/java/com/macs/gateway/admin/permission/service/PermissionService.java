package com.macs.gateway.admin.permission.service;

import com.macs.gateway.admin.permission.domain.Permission;
import com.macs.gateway.admin.permission.domain.PermissionId;
import com.macs.gateway.admin.permission.dto.PermissionEntry;
import com.macs.gateway.admin.permission.dto.PermissionRequest;
import com.macs.gateway.admin.permission.dto.PermissionResponse;
import com.macs.gateway.admin.permission.dto.UserPermissionsResponse;
import com.macs.gateway.admin.permission.repository.PermissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    private final PermissionRepository repository;

    public PermissionService(PermissionRepository repository) {
        this.repository = repository;
    }

    public Flux<PermissionResponse> list(String appName, String employeeNumber) {
        boolean hasApp = appName != null && !appName.isBlank();
        boolean hasEmp = employeeNumber != null && !employeeNumber.isBlank();
        Flux<Permission> rows;
        if (hasApp && hasEmp) {
            rows = repository.findByAppNameAndEmployeeNumber(appName, employeeNumber);
        } else if (hasApp) {
            rows = repository.findByAppName(appName);
        } else if (hasEmp) {
            rows = repository.findByEmployeeNumber(employeeNumber);
        } else {
            rows = repository.findAll();
        }
        return rows.map(PermissionResponse::from);
    }

    public Mono<UserPermissionsResponse> forUser(String appName, String employeeNumber) {
        return repository.findByAppNameAndEmployeeNumber(appName, employeeNumber)
                .map(p -> new PermissionEntry(
                        p.getId().system(),
                        p.getId().connector(),
                        p.getRole()))
                .collectList()
                .map(entries -> new UserPermissionsResponse(appName, employeeNumber, entries));
    }

    public Mono<PermissionResponse> grant(PermissionRequest req) {
        return Mono.fromRunnable(() -> validate(req))
                .then(Mono.defer(() -> {
                    PermissionId id = toId(req);
                    return repository.existsById(id).flatMap(exists -> {
                        if (exists) {
                            log.warn("Permission grant CONFLICT app={} emp={} system={} connector={}",
                                    req.appName(), req.employeeNumber(), req.system(), req.connector());
                            return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                    "Permission already exists for this (user, system, connector)"));
                        }
                        return repository.insert(new Permission(id, req.role()))
                                .doOnNext(p -> log.info(
                                        "Permission GRANTED app={} emp={} system={} connector={} role={}",
                                        req.appName(), req.employeeNumber(), req.system(),
                                        req.connector(), req.role()))
                                .map(PermissionResponse::from);
                    });
                }));
    }

    public Mono<PermissionResponse> updateRole(PermissionRequest req) {
        return Mono.fromRunnable(() -> validate(req))
                .then(Mono.defer(() -> {
                    PermissionId id = toId(req);
                    return repository.findById(id)
                            .switchIfEmpty(Mono.error(() -> {
                                log.warn("Permission updateRole NOT_FOUND app={} emp={} system={} connector={}",
                                        req.appName(), req.employeeNumber(), req.system(), req.connector());
                                return new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission not found");
                            }))
                            .flatMap(existing -> {
                                String previousRole = existing.getRole();
                                return repository.updateRole(id, req.role())
                                        .doOnNext(p -> log.info(
                                                "Permission role CHANGED app={} emp={} system={} connector={} {} -> {}",
                                                req.appName(), req.employeeNumber(), req.system(), req.connector(),
                                                previousRole, req.role()))
                                        .map(PermissionResponse::from);
                            });
                }));
    }

    public Mono<Void> revoke(String appName, String employeeNumber, String system, String connector) {
        PermissionId id = new PermissionId(appName, employeeNumber, system, connector);
        return repository.existsById(id).flatMap(exists -> {
            if (!exists) {
                log.warn("Permission revoke NOT_FOUND app={} emp={} system={} connector={}",
                        appName, employeeNumber, system, connector);
                return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission not found"));
            }
            return repository.deleteById(id)
                    .doOnSuccess(v -> log.info("Permission REVOKED app={} emp={} system={} connector={}",
                            appName, employeeNumber, system, connector));
        });
    }

    private PermissionId toId(PermissionRequest req) {
        return new PermissionId(req.appName(), req.employeeNumber(), req.system(), req.connector());
    }

    private void validate(PermissionRequest req) {
        if (req.appName() == null || req.appName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "appName required");
        }
        if (req.employeeNumber() == null || req.employeeNumber().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "employeeNumber required");
        }
        if (req.system() == null || req.system().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "system required");
        }
        if (req.connector() == null || req.connector().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "connector required");
        }
        if (req.role() == null || req.role().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role required");
        }
    }
}
