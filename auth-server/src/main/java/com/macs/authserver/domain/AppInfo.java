package com.macs.authserver.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("APP_INFO")
public class AppInfo implements Persistable<String> {

    @Id
    @Column("APP_ID")
    private String appId;

    @Column("APP_NAME")
    private String appName;

    @Column("DESCRIPTION")
    private String description;

    @Column("CREATED_AT")
    private LocalDateTime createdAt;

    @Transient
    private boolean newEntity;

    protected AppInfo() {
    }

    public AppInfo(String appId, String appName, String description) {
        this.appId = appId;
        this.appName = appName;
        this.description = description;
        this.createdAt = LocalDateTime.now();
        this.newEntity = true;
    }

    @Override
    public String getId() {
        return appId;
    }

    @Override
    @Transient
    public boolean isNew() {
        return newEntity;
    }

    public String getAppId() {
        return appId;
    }

    public String getAppName() {
        return appName;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
