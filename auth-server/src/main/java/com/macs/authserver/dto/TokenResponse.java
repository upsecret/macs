package com.macs.authserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token issuance response")
public record TokenResponse(
        String token,

        @JsonProperty("client_app")
        String clientApp,

        @JsonProperty("employee_number")
        String employeeNumber
) {
}
