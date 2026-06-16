package com.macs.authserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token issuance request")
public record TokenRequest(
        @JsonProperty("employee_number")
        @Schema(description = "Employee number", example = "2078432")
        String employeeNumber,

        @JsonProperty("client_app")
        @Schema(description = "Client application the token is bound to", example = "portal")
        String clientApp
) {
}
