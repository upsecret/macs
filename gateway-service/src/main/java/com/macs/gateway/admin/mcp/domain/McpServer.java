package com.macs.gateway.admin.mcp.domain;

import java.time.LocalDateTime;

public class McpServer {

    private final String id;
    private String name;
    private String description;
    private String endpointUrl;
    private String transport;
    private String authType;
    private String authToken;
    private String system;
    private final LocalDateTime createdAt;

    public McpServer(String id, String name, String description, String endpointUrl,
                     String transport, String authType, String authToken, String system) {
        this(id, name, description, endpointUrl, transport, authType, authToken, system, null);
    }

    public McpServer(String id, String name, String description, String endpointUrl,
                     String transport, String authType, String authToken, String system,
                     LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.endpointUrl = endpointUrl;
        this.transport = transport;
        this.authType = authType;
        this.authToken = authToken;
        this.system = system;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }
    public String getTransport() { return transport; }
    public void setTransport(String transport) { this.transport = transport; }
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }
    public String getSystem() { return system; }
    public void setSystem(String system) { this.system = system; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
