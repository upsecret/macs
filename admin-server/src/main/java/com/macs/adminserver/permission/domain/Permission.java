package com.macs.adminserver.permission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "PERMISSION", schema = "MACS")
public class Permission {

    @EmbeddedId
    private PermissionId id;

    @Column(name = "ROLE", length = 32, nullable = false)
    private String role;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Permission() {
    }

    public Permission(PermissionId id, String role) {
        this.id = id;
        this.role = role;
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
