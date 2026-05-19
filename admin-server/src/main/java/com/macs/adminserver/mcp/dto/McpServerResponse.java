package com.macs.adminserver.mcp.dto;

import com.macs.adminserver.mcp.domain.McpServer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Registered MCP server")
public record McpServerResponse(
        String id,
        String name,
        String description,
        String endpointUrl,
        String transport,
        String authType,
        boolean hasAuthToken,
        String system,
        LocalDateTime createdAt
) {
    public static McpServerResponse of(McpServer s) {
        return new McpServerResponse(
                s.getId(),
                s.getName(),
                s.getDescription(),
                s.getEndpointUrl(),
                s.getTransport(),
                s.getAuthType(),
                s.getAuthToken() != null && !s.getAuthToken().isBlank(),
                s.getSystem(),
                s.getCreatedAt()
        );
    }
}
