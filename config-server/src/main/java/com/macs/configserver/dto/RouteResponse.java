package com.macs.configserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Gateway route response")
public record RouteResponse(
        String id,
        String uri,
        List<GatewayDefinition> predicates,
        List<GatewayDefinition> filters,
        int order
) {
}
