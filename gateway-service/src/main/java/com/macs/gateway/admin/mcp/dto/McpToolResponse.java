package com.macs.gateway.admin.mcp.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "MCP tool descriptor (from tools/list)")
public record McpToolResponse(
        String name,
        String description,
        @Schema(description = "JSON Schema for the tool's arguments") JsonNode inputSchema
) {
}
