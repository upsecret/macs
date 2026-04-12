package com.macs.adminserver.permission.service;

import com.macs.adminserver.permission.domain.Permission;
import com.macs.adminserver.permission.domain.PermissionId;
import com.macs.adminserver.permission.dto.PermissionEntry;
import com.macs.adminserver.permission.dto.PermissionRequest;
import com.macs.adminserver.permission.dto.PermissionResponse;
import com.macs.adminserver.permission.dto.UserPermissionsResponse;
import com.macs.adminserver.permission.repository.PermissionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PermissionService {

    private final PermissionRepository repository;

    public PermissionService(PermissionRepository repository) {
        this.repository = repository;
    }

    public List<PermissionResponse> list(String appName, String employeeNumber) {
        List<Permission> rows;
        if (appName != null && !appName.isBlank()
                && employeeNumber != null && !employeeNumber.isBlank()) {
            rows = repository.findByIdAppNameAndIdEmployeeNumber(appName, employeeNumber);
        } else if (appName != null && !appName.isBlank()) {
            rows = repository.findByIdAppName(appName);
        } else {
            rows = repository.findAll();
        }
        return rows.stream().map(PermissionResponse::from).toList();
    }

    public UserPermissionsResponse forUser(String appName, String employeeNumber) {
        List<PermissionEntry> entries = repository
                .findByIdAppNameAndIdEmployeeNumber(appName, employeeNumber)
                .stream()
                .map(p -> new PermissionEntry(
                        p.getId().getSystem(),
                        p.getId().getConnector(),
                        p.getRole()))
                .toList();
        return new UserPermissionsResponse(appName, employeeNumber, entries);
    }

    @Transactional
    public PermissionResponse grant(PermissionRequest req) {
        validate(req);
        PermissionId id = new PermissionId(
                req.appName(), req.employeeNumber(), req.system(), req.connector());
        if (repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Permission already exists for this (user, system, connector)");
        }
        Permission saved = repository.save(new Permission(id, req.role()));
        return PermissionResponse.from(saved);
    }

    @Transactional
    public PermissionResponse updateRole(PermissionRequest req) {
        validate(req);
        PermissionId id = new PermissionId(
                req.appName(), req.employeeNumber(), req.system(), req.connector());
        Permission entity = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Permission not found"));
        entity.setRole(req.role());
        return PermissionResponse.from(repository.save(entity));
    }

    @Transactional
    public void revoke(String appName, String employeeNumber, String system, String connector) {
        PermissionId id = new PermissionId(appName, employeeNumber, system, connector);
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission not found");
        }
        repository.deleteById(id);
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
