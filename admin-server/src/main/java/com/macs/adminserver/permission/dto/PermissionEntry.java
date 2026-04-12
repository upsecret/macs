package com.macs.adminserver.permission.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Permission entry embedded in JWT claim")
public record PermissionEntry(
        String system,
        String connector,
        String role
) {
}
