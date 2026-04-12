package com.macs.adminserver.permission.repository;

import com.macs.adminserver.permission.domain.Permission;
import com.macs.adminserver.permission.domain.PermissionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, PermissionId> {

    List<Permission> findByIdAppNameAndIdEmployeeNumber(String appName, String employeeNumber);

    List<Permission> findByIdAppName(String appName);
}
