package com.macs.gateway.admin.audit.controller;

import com.macs.gateway.admin.audit.dto.AuditLogResponse;
import com.macs.gateway.admin.audit.dto.PagedResponse;
import com.macs.gateway.admin.audit.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/audit")
@Tag(name = "Audit Log", description = "Admin operation audit log")
public class AuditLogController {

    private final AuditLogService service;

    public AuditLogController(AuditLogService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Search audit log entries")
    public Mono<PagedResponse<AuditLogResponse>> search(
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to,
            @RequestParam(required = false) String employeeNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.search(from, to, employeeNumber, page, size);
    }
}
