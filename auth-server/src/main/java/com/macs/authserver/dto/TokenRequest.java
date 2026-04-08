package com.macs.authserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token issuance request")
public record TokenRequest(
        @JsonProperty("app_name")
        @Schema(description = "Application name", example = "portal")
        String appName,

        @JsonProperty("employee_number")
        @Schema(description = "Employee number", example = "EMP001")
        String employeeNumber
) {
}
