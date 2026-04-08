package com.macs.authserver.domain;

public record UserResource(
        String employeeNumber,
        String appId,
        String resourceName
) {
}
