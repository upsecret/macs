package com.macs.gateway.admin.permission.bootstrap;

import com.macs.gateway.admin.permission.domain.Permission;
import com.macs.gateway.admin.permission.domain.PermissionId;
import com.macs.gateway.admin.permission.repository.PermissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class PermissionBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PermissionBootstrapRunner.class);

    private final PermissionRepository repository;

    @Value("${macs.bootstrap.admin.enabled:true}")
    private boolean enabled;

    @Value("${macs.bootstrap.admin.employee-number:2078432}")
    private String employeeNumber;

    @Value("${macs.bootstrap.admin.app-name:portal}")
    private String appName;

    @Value("${macs.bootstrap.admin.system:common}")
    private String system;

    @Value("${macs.bootstrap.admin.connector:portal}")
    private String connector;

    @Value("${macs.bootstrap.admin.role:admin}")
    private String role;

    public PermissionBootstrapRunner(PermissionRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Permission bootstrap disabled (macs.bootstrap.admin.enabled=false)");
            return;
        }
        PermissionId id = new PermissionId(appName, employeeNumber, system, connector);
        repository.existsById(id)
                .flatMap(exists -> {
                    if (exists) {
                        log.info("Permission bootstrap: {}/{} already present", appName, employeeNumber);
                        return reactor.core.publisher.Mono.empty();
                    }
                    return repository.insert(new Permission(id, role))
                            .doOnNext(p -> log.info("Permission bootstrap: inserted {}/{} [{}/{}/{}]",
                                    appName, employeeNumber, system, connector, role));
                })
                .doOnError(ex -> log.error("Permission bootstrap failed: {}", ex.getMessage(), ex))
                .block();
    }
}
