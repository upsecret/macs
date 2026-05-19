package com.macs.adminserver.mcp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "MCP server create/update payload")
public record McpServerRequest(
        @Schema(example = "dummy-mcp") String id,
        @Schema(example = "Dummy MCP Server") String name,
        @Schema(example = "Built-in test server") String description,
        @Schema(example = "http://dummy-mcp-server:8765/mcp") String endpointUrl,
        @Schema(allowableValues = {"streamable-http"}, example = "streamable-http") String transport,
        @Schema(allowableValues = {"none", "bearer"}, example = "none") String authType,
        @Schema(description = "Bearer 토큰 (authType=bearer 일 때만)") String authToken,
        @Schema(example = "common") String system
) {
}
