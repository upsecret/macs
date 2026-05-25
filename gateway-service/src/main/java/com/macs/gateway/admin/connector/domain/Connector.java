package com.macs.gateway.admin.connector.domain;

import java.time.LocalDateTime;

public class Connector {

    private final String id;
    private String title;
    private String description;
    private String type;
    private String system;
    private String docsUrl;
    private final LocalDateTime createdAt;

    public Connector(String id, String title, String description, String type,
                     String system, String docsUrl) {
        this(id, title, description, type, system, docsUrl, null);
    }

    public Connector(String id, String title, String description, String type,
                     String system, String docsUrl, LocalDateTime createdAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.type = type;
        this.system = system;
        this.docsUrl = docsUrl;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSystem() { return system; }
    public void setSystem(String system) { this.system = system; }

    public String getDocsUrl() { return docsUrl; }
    public void setDocsUrl(String docsUrl) { this.docsUrl = docsUrl; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
