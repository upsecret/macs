package com.macs.authserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token validation + permission check request")
public record ValidationRequest(
        @JsonProperty("app_name")
        @Schema(description = "Client application name (from app_name header on the original request). Must match the client_app claim in the token.", example = "portal")
        String appName,

        @JsonProperty("employee_number")
        @Schema(description = "Employee number (from employee_number header on the original request). Must match the employee_number claim in the token.", example = "2078432")
        String employeeNumber,

        @JsonProperty("connector")
        @Schema(description = "Route id (= connector) the caller is trying to access. Omit to validate token + identity only.", example = "auth-route")
        String connector
) {
}
