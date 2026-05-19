package com.macs.adminserver.mcp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "MCP_SERVER", schema = "MACS")
public class McpServer {

    @Id
    @Column(name = "ID", length = 128, nullable = false)
    private String id;

    @Column(name = "NAME", length = 256, nullable = false)
    private String name;

    @Column(name = "DESCRIPTION", length = 1024)
    private String description;

    @Column(name = "ENDPOINT_URL", length = 1024, nullable = false)
    private String endpointUrl;

    @Column(name = "TRANSPORT", length = 32, nullable = false)
    private String transport;

    @Column(name = "AUTH_TYPE", length = 32, nullable = false)
    private String authType;

    @Column(name = "AUTH_TOKEN", length = 2048)
    private String authToken;

    @Column(name = "SYSTEM", length = 64, nullable = false)
    private String system;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected McpServer() {
    }

    public McpServer(String id, String name, String description, String endpointUrl,
                     String transport, String authType, String authToken, String system) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.endpointUrl = endpointUrl;
        this.transport = transport;
        this.authType = authType;
        this.authToken = authToken;
        this.system = system;
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
