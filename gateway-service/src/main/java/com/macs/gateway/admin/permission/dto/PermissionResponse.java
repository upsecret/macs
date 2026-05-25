package com.macs.gateway.admin.permission.dto;

import com.macs.gateway.admin.permission.domain.Permission;
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
                entity.getId().appName(),
                entity.getId().employeeNumber(),
                entity.getId().system(),
                entity.getId().connector(),
                entity.getRole(),
                entity.getCreatedAt());
    }
}
