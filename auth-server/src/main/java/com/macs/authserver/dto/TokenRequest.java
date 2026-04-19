package com.macs.authserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token issuance request")
public record TokenRequest(
        @JsonProperty("client_app")
        @Schema(description = "Client application identifier (from Client-App header)", example = "portal")
        String clientApp,

        @JsonProperty("employee_number")
        @Schema(description = "Employee number (from Employee-Number header)", example = "2078432")
        String employeeNumber
) {
}
