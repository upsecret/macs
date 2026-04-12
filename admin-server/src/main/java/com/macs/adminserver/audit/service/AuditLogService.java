package com.macs.adminserver.audit.service;

import com.macs.adminserver.audit.domain.AuditLog;
import com.macs.adminserver.audit.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class AuditLogService {

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AuditLog record(String employeeNumber, String action, String targetType,
                           String targetId, String result, String detail) {
        return repository.save(new AuditLog(employeeNumber, action, targetType, targetId, result, detail));
    }

    public Page<AuditLog> search(LocalDateTime from, LocalDateTime to, String employeeNumber, Pageable pageable) {
        return repository.search(from, to, employeeNumber, pageable);
    }
}
