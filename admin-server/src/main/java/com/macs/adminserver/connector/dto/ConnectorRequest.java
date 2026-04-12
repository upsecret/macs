package com.macs.adminserver.connector.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Connector create/update payload")
public record ConnectorRequest(
        String id,
        String title,
        String description,
        String type
) {
}
