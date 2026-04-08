package com.macs.authserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token validation request")
public record ValidationRequest(
        @JsonProperty("request_app")
        @Schema(description = "Requested resource path", example = "/api/v1/users")
        String requestApp
) {
}
