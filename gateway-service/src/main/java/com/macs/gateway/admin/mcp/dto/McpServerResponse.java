package com.macs.gateway.admin.mcp.dto;

import com.macs.gateway.admin.mcp.domain.McpServer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Registered MCP server (bound to a gateway route)")
public record McpServerResponse(
        String id,
        String name,
        String description,
        @Schema(description = "업스트림 주소 (라우트 uri 의 denormalize, 내부용)")
        String endpointUrl,
        @Schema(description = "게이트웨이 라우트 경로 (예: /mcp/dummy-mcp). 연동은 이 경로로 한다.")
        String gatewayPath,
        @Schema(description = "매칭 게이트웨이 라우트 존재 여부")
        boolean active,
        String transport,
        String authType,
        boolean hasAuthToken,
        String system,
        LocalDateTime createdAt
) {
    public static McpServerResponse of(McpServer s, String gatewayPath) {
        return new McpServerResponse(
                s.getId(),
                s.getName(),
                s.getDescription(),
                s.getEndpointUrl(),
                gatewayPath,
                gatewayPath != null && !gatewayPath.isBlank(),
                s.getTransport(),
                s.getAuthType(),
                s.getAuthToken() != null && !s.getAuthToken().isBlank(),
                s.getSystem(),
                s.getCreatedAt()
        );
    }
}
