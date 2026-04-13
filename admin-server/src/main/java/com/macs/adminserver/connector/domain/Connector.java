package com.macs.adminserver.connector.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "CONNECTOR", schema = "MACS")
public class Connector {

    @Id
    @Column(name = "ID", length = 128, nullable = false)
    private String id;

    @Column(name = "TITLE", length = 256, nullable = false)
    private String title;

    @Column(name = "DESCRIPTION", length = 1024)
    private String description;

    @Column(name = "TYPE", length = 16, nullable = false)
    private String type;

    @Column(name = "DOCS_URL", length = 512)
    private String docsUrl;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Connector() {
    }

    public Connector(String id, String title, String description, String type, String docsUrl) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.type = type;
        this.docsUrl = docsUrl;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDocsUrl() {
        return docsUrl;
    }

    public void setDocsUrl(String docsUrl) {
        this.docsUrl = docsUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
