package com.macs.authserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token validation + permission check request")
public record ValidationRequest(
        @JsonProperty("client_app")
        @Schema(description = "Client application identifier (from Client-App header on the original request)", example = "portal")
        String clientApp,

        @JsonProperty("employee_number")
        @Schema(description = "Employee number (from Employee-Number header on the original request)", example = "2078432")
        String employeeNumber,

        @JsonProperty("connector")
        @Schema(description = "Route id (= connector) the caller is trying to access. Required for permission check.", example = "auth-route")
        String connector
) {
}
