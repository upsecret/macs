package com.macs.adminserver.permission.dto;

import com.macs.adminserver.permission.domain.Permission;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Permission row")
public record PermissionResponse(
        String appName,
        String employeeNumber,
        String system,
        String connector,
        String role,
        LocalDateTime createdAt
) {
    public static PermissionResponse from(Permission entity) {
        return new PermissionResponse(
                entity.getId().getAppName(),
                entity.getId().getEmployeeNumber(),
                entity.getId().getSystem(),
                entity.getId().getConnector(),
                entity.getRole(),
                entity.getCreatedAt());
    }
}
