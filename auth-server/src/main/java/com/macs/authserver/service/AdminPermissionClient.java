package com.macs.authserver.service;

import com.macs.authserver.dto.PermissionEntry;
import com.macs.authserver.dto.UserPermissionsResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class AdminPermissionClient {

    private final WebClient adminServerWebClient;

    public AdminPermissionClient(WebClient adminServerWebClient) {
        this.adminServerWebClient = adminServerWebClient;
    }

    public Mono<List<PermissionEntry>> fetch(String appName, String employeeNumber) {
        return adminServerWebClient.get()
                .uri("/api/admin/permissions/users/{app}/{emp}", appName, employeeNumber)
                .retrieve()
                .bodyToMono(UserPermissionsResponse.class)
                .map(UserPermissionsResponse::permissions)
                .defaultIfEmpty(List.of());
    }
}
