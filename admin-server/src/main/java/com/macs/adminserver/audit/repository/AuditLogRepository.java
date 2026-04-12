package com.macs.adminserver.audit.repository;

import com.macs.adminserver.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:from IS NULL OR a.occurredAt >= :from)
              AND (:to IS NULL OR a.occurredAt < :to)
              AND (:employeeNumber IS NULL OR a.employeeNumber = :employeeNumber)
            ORDER BY a.occurredAt DESC
            """)
    Page<AuditLog> search(@Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to,
                          @Param("employeeNumber") String employeeNumber,
                          Pageable pageable);
}
