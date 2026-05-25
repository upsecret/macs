package com.macs.gateway.admin.permission.domain;

import java.time.LocalDateTime;

public class Permission {

    private final PermissionId id;
    private String role;
    private final LocalDateTime createdAt;

    public Permission(PermissionId id, String role) {
        this(id, role, null);
    }

    public Permission(PermissionId id, String role, LocalDateTime createdAt) {
        this.id = id;
        this.role = role;
        this.createdAt = createdAt;
    }

    public PermissionId getId() {
        return id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
