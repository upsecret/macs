package com.macs.gateway.admin.mcp.controller;

import com.macs.gateway.admin.mcp.dto.McpServerRequest;
import com.macs.gateway.admin.mcp.dto.McpServerResponse;
import com.macs.gateway.admin.mcp.dto.McpToolCallRequest;
import com.macs.gateway.admin.mcp.dto.McpToolCallResponse;
import com.macs.gateway.admin.mcp.dto.McpToolResponse;
import com.macs.gateway.admin.mcp.service.McpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/mcp")
@Tag(name = "MCP", description = "Model Context Protocol server registry & invocation proxy")
public class McpController {

    private final McpService service;

    public McpController(McpService service) {
        this.service = service;
    }

    @GetMapping("/servers")
    @Operation(summary = "List registered MCP servers")
    public Flux<McpServerResponse> listServers() {
        return service.list();
    }

    @GetMapping("/servers/{id}")
    @Operation(summary = "Get a single MCP server")
    public Mono<McpServerResponse> getServer(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping("/servers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new MCP server")
    public Mono<McpServerResponse> createServer(@RequestBody McpServerRequest request) {
        return service.create(request);
    }

    @PutMapping("/servers/{id}")
    @Operation(summary = "Update an MCP server")
    public Mono<McpServerResponse> updateServer(@PathVariable String id,
                                                 @RequestBody McpServerRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/servers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an MCP server")
    public Mono<Void> deleteServer(@PathVariable String id) {
        return service.delete(id);
    }

    @GetMapping("/servers/{id}/tools")
    @Operation(summary = "Fetch tools/list from the MCP server")
    public Flux<McpToolResponse> listTools(@PathVariable String id) {
        return service.listTools(id);
    }

    @PostMapping("/servers/{id}/tools/call")
    @Operation(summary = "Invoke a tool via tools/call on the MCP server")
    public Mono<McpToolCallResponse> callTool(@PathVariable String id,
                                              @RequestBody McpToolCallRequest request) {
        return service.callTool(id, request);
    }

    @GetMapping("/servers/{id}/health")
    @Operation(summary = "Probe connectivity to the MCP server (calls initialize)")
    public Mono<Map<String, Object>> health(@PathVariable String id) {
        return service.healthCheck(id).map(ok -> Map.of("id", id, "healthy", ok));
    }
}
