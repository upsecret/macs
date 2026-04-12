package com.macs.authserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token validation response")
public record ValidationResponse(
        boolean valid,

        @JsonProperty("app_name")
        String appName,

        @JsonProperty("employee_number")
        String employeeNumber
) {
}
