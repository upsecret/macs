package com.macs.adminserver.permission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class PermissionId implements Serializable {

    @Column(name = "APP_NAME", length = 64, nullable = false)
    private String appName;

    @Column(name = "EMPLOYEE_NUMBER", length = 32, nullable = false)
    private String employeeNumber;

    @Column(name = "SYSTEM", length = 64, nullable = false)
    private String system;

    @Column(name = "CONNECTOR", length = 128, nullable = false)
    private String connector;

    protected PermissionId() {
    }

    public PermissionId(String appName, String employeeNumber, String system, String connector) {
        this.appName = appName;
        this.employeeNumber = employeeNumber;
        this.system = system;
        this.connector = connector;
    }

    public String getAppName() {
        return appName;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public String getSystem() {
        return system;
    }

    public String getConnector() {
        return connector;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PermissionId that)) return false;
        return Objects.equals(appName, that.appName)
                && Objects.equals(employeeNumber, that.employeeNumber)
                && Objects.equals(system, that.system)
                && Objects.equals(connector, that.connector);
    }

    @Override
    public int hashCode() {
        return Objects.hash(appName, employeeNumber, system, connector);
    }
}
