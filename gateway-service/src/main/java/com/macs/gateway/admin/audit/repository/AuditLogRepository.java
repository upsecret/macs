package com.macs.gateway.admin.audit.repository;

import com.macs.gateway.admin.audit.domain.AuditLog;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface AuditLogRepository extends ReactiveCrudRepository<AuditLog, Long> {
}
