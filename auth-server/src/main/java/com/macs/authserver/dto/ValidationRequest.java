package com.macs.authserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token validation request")
public record ValidationRequest(
        @JsonProperty("connector")
        @Schema(description = "Logical connector the caller is trying to access", example = "portal")
        String connector
) {
}
