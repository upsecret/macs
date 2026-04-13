package com.macs.authserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token validation + permission check request")
public record ValidationRequest(
        @JsonProperty("app_name")
        @Schema(description = "Client application name (from app_name header on the original request)", example = "portal")
        String appName,

        @JsonProperty("connector")
        @Schema(description = "Route id (= connector) the caller is trying to access. Omit to validate token only.", example = "auth-route")
        String connector
) {
}
