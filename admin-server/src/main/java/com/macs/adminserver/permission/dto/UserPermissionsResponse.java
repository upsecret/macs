package com.macs.adminserver.permission.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Permissions for a specific user — used by auth-server for token issuance")
public record UserPermissionsResponse(
        String appName,
        String employeeNumber,
        List<PermissionEntry> permissions
) {
}
