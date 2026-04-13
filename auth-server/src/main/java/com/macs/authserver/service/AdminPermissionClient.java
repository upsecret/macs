package com.macs.authserver.service;

import com.macs.authserver.dto.PermissionEntry;
import com.macs.authserver.dto.UserPermissionsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class AdminPermissionClient {

    private static final Logger log = LoggerFactory.getLogger(AdminPermissionClient.class);

    private final WebClient adminServerWebClient;

    public AdminPermissionClient(WebClient adminServerWebClient) {
        this.adminServerWebClient = adminServerWebClient;
    }

    public Mono<List<PermissionEntry>> fetch(String appName, String employeeNumber) {
        log.info("Fetching permissions from admin-server app={} emp={}", appName, employeeNumber);
        return adminServerWebClient.get()
                .uri("/api/admin/permissions/users/{app}/{emp}", appName, employeeNumber)
                .retrieve()
                .bodyToMono(UserPermissionsResponse.class)
                .map(UserPermissionsResponse::permissions)
                .defaultIfEmpty(List.of())
                .doOnNext(perms ->
                        log.info("Permissions received app={} emp={} count={}",
                                appName, employeeNumber, perms.size()))
                .doOnError(err ->
                        log.error("Permissions fetch failed app={} emp={} type={} msg={}",
                                appName, employeeNumber,
                                err.getClass().getSimpleName(), err.getMessage()));
    }
}
