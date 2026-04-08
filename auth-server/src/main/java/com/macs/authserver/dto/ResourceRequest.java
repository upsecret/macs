package com.macs.authserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resource request")
public record ResourceRequest(
        @Schema(description = "Resource path pattern", example = "/api/v1/**")
        String resourceName
) {
}
