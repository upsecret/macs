package com.macs.authserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token validation + permission check response")
public record ValidationResponse(
        boolean valid,

        boolean allowed,

        @JsonProperty("employee_number")
        String employeeNumber
) {
}
