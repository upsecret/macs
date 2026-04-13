package com.macs.adminserver.connector.dto;

import com.macs.adminserver.connector.domain.Connector;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Connector detail with derived active state")
public record ConnectorResponse(
        String id,
        String title,
        String description,
        String type,
        boolean active,
        String uri,
        String docsUrl,
        LocalDateTime createdAt
) {
    public static ConnectorResponse of(Connector entity, boolean active, String uri) {
        return new ConnectorResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getType(),
                active,
                uri,
                entity.getDocsUrl(),
                entity.getCreatedAt());
    }
}
