package com.macs.adminserver.permission.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Permission grant request")
public record PermissionRequest(
        String appName,
        String employeeNumber,
        String system,
        String connector,
        String role
) {
}
