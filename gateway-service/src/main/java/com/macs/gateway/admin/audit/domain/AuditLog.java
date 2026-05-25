package com.macs.gateway.admin.audit.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("AUDIT_LOG")
public class AuditLog {

    @Id
    @Column("AUDIT_ID")
    private Long auditId;

    @ReadOnlyProperty
    @Column("OCCURRED_AT")
    private LocalDateTime occurredAt;

    @Column("EMPLOYEE_NUMBER")
    private String employeeNumber;

    @Column("ACTION")
    private String action;

    @Column("TARGET_TYPE")
    private String targetType;

    @Column("TARGET_ID")
    private String targetId;

    @Column("RESULT")
    private String result;

    @Column("DETAIL")
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
