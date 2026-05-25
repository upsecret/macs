package com.macs.gateway.admin.permission.domain;

public record PermissionId(
        String appName,
        String employeeNumber,
        String system,
        String connector
) {
}
