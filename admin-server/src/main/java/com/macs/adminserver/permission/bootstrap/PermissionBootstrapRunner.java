package com.macs.adminserver.permission.bootstrap;

import com.macs.adminserver.permission.domain.Permission;
import com.macs.adminserver.permission.domain.PermissionId;
import com.macs.adminserver.permission.repository.PermissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Permission bootstrap disabled (macs.bootstrap.admin.enabled=false)");
            return;
        }
        PermissionId id = new PermissionId(appName, employeeNumber, system, connector);
        if (repository.existsById(id)) {
            log.info("Permission bootstrap: {}/{} already present", appName, employeeNumber);
            return;
        }
        repository.save(new Permission(id, role));
        log.info("Permission bootstrap: inserted {}/{} [{}/{}/{}]",
                appName, employeeNumber, system, connector, role);
    }
}
