package com.macs.authserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Token issuance response")
public record TokenResponse(
        String token,

        @JsonProperty("app_name")
        String appName,

        @JsonProperty("employee_number")
        String employeeNumber,

        List<PermissionEntry> permissions
) {
}
