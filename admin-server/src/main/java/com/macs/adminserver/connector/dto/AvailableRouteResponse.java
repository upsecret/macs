package com.macs.adminserver.connector.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Gateway route available for connector registration")
public record AvailableRouteResponse(
        String id,
        String uri
) {
}
