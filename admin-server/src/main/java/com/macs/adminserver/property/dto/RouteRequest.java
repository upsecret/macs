package com.macs.adminserver.property.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Gateway route request")
public record RouteRequest(
        @Schema(description = "Route ID", example = "portal-route")
        String id,
        @Schema(description = "Target URI", example = "http://portal:3000")
        String uri,
        @Schema(description = "Route predicates (name + args)")
        List<GatewayDefinition> predicates,
        @Schema(description = "Route filters (name + args)")
        List<GatewayDefinition> filters,
        @Schema(description = "Route order (lower = higher priority)", example = "0")
        Integer order,
        @Schema(description = "Also register this route in gateway swagger. Default true.",
                example = "true")
        Boolean registerSwagger
) {
}
