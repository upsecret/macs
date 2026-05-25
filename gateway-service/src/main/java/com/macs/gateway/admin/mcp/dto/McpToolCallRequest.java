package com.macs.gateway.admin.mcp.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tool invocation payload — name + JSON arguments")
public record McpToolCallRequest(
        @Schema(example = "echo") String name,
        @Schema(description = "Arguments object matching the tool's inputSchema") JsonNode arguments
) {
}
