package com.macs.gateway.admin.connector.dto;

import com.macs.gateway.admin.connector.domain.Connector;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Connector detail with derived active state")
public record ConnectorResponse(
        String id,
        String title,
        String description,
        String type,
        String system,
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
                entity.getSystem(),
                active,
                uri,
                entity.getDocsUrl(),
                entity.getCreatedAt());
    }
}
