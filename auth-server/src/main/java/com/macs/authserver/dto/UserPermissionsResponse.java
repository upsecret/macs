package com.macs.authserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Permissions payload received from admin-server")
public record UserPermissionsResponse(
        String appName,
        String employeeNumber,
        List<PermissionEntry> permissions
) {
}
