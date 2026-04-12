package com.macs.adminserver.audit.dto;

import com.macs.adminserver.audit.domain.AuditLog;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long auditId,
        LocalDateTime occurredAt,
        String employeeNumber,
        String action,
        String targetType,
        String targetId,
        String result,
        String detail
) {
    public static AuditLogResponse from(AuditLog entity) {
        return new AuditLogResponse(
                entity.getAuditId(),
                entity.getOccurredAt(),
                entity.getEmployeeNumber(),
                entity.getAction(),
                entity.getTargetType(),
                entity.getTargetId(),
                entity.getResult(),
                entity.getDetail());
    }
}
