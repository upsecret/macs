package com.macs.adminserver.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "AUDIT_LOG", schema = "MACS")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AUDIT_ID")
    private Long auditId;

    @Column(name = "OCCURRED_AT", nullable = false, insertable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Column(name = "EMPLOYEE_NUMBER", length = 32)
    private String employeeNumber;

    @Column(name = "ACTION", length = 64, nullable = false)
    private String action;

    @Column(name = "TARGET_TYPE", length = 64)
    private String targetType;

    @Column(name = "TARGET_ID", length = 256)
    private String targetId;

    @Column(name = "RESULT", length = 32)
    private String result;

    @Column(name = "DETAIL", length = 4000)
    private String detail;

    protected AuditLog() {
    }

    public AuditLog(String employeeNumber, String action, String targetType,
                    String targetId, String result, String detail) {
        this.employeeNumber = employeeNumber;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.result = result;
        this.detail = detail;
    }

    public Long getAuditId() {
        return auditId;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getResult() {
        return result;
    }

    public String getDetail() {
        return detail;
    }
}
