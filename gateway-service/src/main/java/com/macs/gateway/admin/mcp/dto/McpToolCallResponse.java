package com.macs.gateway.admin.mcp.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Normalized tool/call result")
public record McpToolCallResponse(
        @Schema(description = "MCP content[] array: each block has type=text/image/resource") List<JsonNode> content,
        @Schema(description = "Whether the server marked this call as an error result (isError)") boolean error,
        @Schema(description = "Raw JSON-RPC result object as returned by the server") JsonNode raw
) {
}
