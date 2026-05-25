package com.macs.gateway.admin.audit.service;

import com.macs.gateway.admin.audit.domain.AuditLog;
import com.macs.gateway.admin.audit.dto.AuditLogResponse;
import com.macs.gateway.admin.audit.dto.PagedResponse;
import com.macs.gateway.admin.audit.repository.AuditLogRepository;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuditLogService {

    private final AuditLogRepository repository;
    private final DatabaseClient db;

    public AuditLogService(AuditLogRepository repository, DatabaseClient db) {
        this.repository = repository;
        this.db = db;
    }

    public Mono<AuditLog> record(String employeeNumber, String action, String targetType,
                                 String targetId, String result, String detail) {
        return repository.save(new AuditLog(employeeNumber, action, targetType, targetId, result, detail));
    }

    public Mono<PagedResponse<AuditLogResponse>> search(LocalDateTime from, LocalDateTime to,
                                                        String employeeNumber, int page, int size) {
        int offset = page * size;
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        if (from != null) { where.append(" AND OCCURRED_AT >= :from"); params.put("from", from); }
        if (to != null) { where.append(" AND OCCURRED_AT < :to"); params.put("to", to); }
        if (employeeNumber != null && !employeeNumber.isBlank()) {
            where.append(" AND EMPLOYEE_NUMBER = :emp");
            params.put("emp", employeeNumber);
        }

        String countSql = "SELECT COUNT(*) AS CNT FROM AUDIT_LOG" + where;
        String pageSql = "SELECT * FROM AUDIT_LOG" + where
                + " ORDER BY OCCURRED_AT DESC OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY";

        DatabaseClient.GenericExecuteSpec countSpec = db.sql(countSql);
        for (Map.Entry<String, Object> e : params.entrySet()) {
            countSpec = countSpec.bind(e.getKey(), e.getValue());
        }

        DatabaseClient.GenericExecuteSpec pageSpec = db.sql(pageSql)
                .bind("offset", offset)
                .bind("size", size);
        for (Map.Entry<String, Object> e : params.entrySet()) {
            pageSpec = pageSpec.bind(e.getKey(), e.getValue());
        }

        Mono<Long> totalMono = countSpec
                .map((row, meta) -> toLong(row.get(0)))
                .one();
        Flux<AuditLogResponse> itemsFlux = pageSpec
                .map((row, meta) -> new AuditLogResponse(
                        toLong(row.get("AUDIT_ID")),
                        row.get("OCCURRED_AT", LocalDateTime.class),
                        row.get("EMPLOYEE_NUMBER", String.class),
                        row.get("ACTION", String.class),
                        row.get("TARGET_TYPE", String.class),
                        row.get("TARGET_ID", String.class),
                        row.get("RESULT", String.class),
                        row.get("DETAIL", String.class)))
                .all();

        final int pageF = page;
        final int sizeF = size;
        return Mono.zip(totalMono, itemsFlux.collectList())
                .map(t -> new PagedResponse<>(t.getT1(), pageF, sizeF, t.getT2()));
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }
}
