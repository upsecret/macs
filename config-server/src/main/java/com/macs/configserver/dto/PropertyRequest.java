package com.macs.configserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Config property request")
public record PropertyRequest(
        @Schema(description = "Application name", example = "gateway-service")
        String application,
        @Schema(description = "Profile", example = "default")
        String profile,
        @Schema(description = "Label (branch)", example = "main")
        String label,
        @Schema(description = "Property key", example = "spring.cloud.gateway.routes[0].id")
        String propKey,
        @Schema(description = "Property value", example = "portal-route")
        String propValue
) {
}
