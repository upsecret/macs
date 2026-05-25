package com.macs.gateway.admin.permission.repository;

import com.macs.gateway.admin.permission.domain.Permission;
import com.macs.gateway.admin.permission.domain.PermissionId;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.function.BiFunction;

@Repository
public class PermissionRepository {

    private static final String COLS = "APP_NAME, EMPLOYEE_NUMBER, SYSTEM, CONNECTOR, ROLE, CREATED_AT";

    private final DatabaseClient db;

    public PermissionRepository(DatabaseClient db) {
        this.db = db;
    }

    public Flux<Permission> findAll() {
        return db.sql("SELECT " + COLS + " FROM PERMISSION")
                .map(rowMapper())
                .all();
    }

    public Flux<Permission> findByAppName(String appName) {
        return db.sql("SELECT " + COLS + " FROM PERMISSION WHERE APP_NAME = :appName")
                .bind("appName", appName)
                .map(rowMapper())
                .all();
    }

    public Flux<Permission> findByAppNameAndEmployeeNumber(String appName, String employeeNumber) {
        return db.sql("SELECT " + COLS + " FROM PERMISSION "
                        + "WHERE APP_NAME = :appName AND EMPLOYEE_NUMBER = :emp")
                .bind("appName", appName)
                .bind("emp", employeeNumber)
                .map(rowMapper())
                .all();
    }

    public Mono<Permission> findById(PermissionId id) {
        return db.sql("SELECT " + COLS + " FROM PERMISSION "
                        + "WHERE APP_NAME = :appName AND EMPLOYEE_NUMBER = :emp "
                        + "AND SYSTEM = :system AND CONNECTOR = :connector")
                .bind("appName", id.appName())
                .bind("emp", id.employeeNumber())
                .bind("system", id.system())
                .bind("connector", id.connector())
                .map(rowMapper())
                .one();
    }

    public Mono<Boolean> existsById(PermissionId id) {
        return findById(id).hasElement();
    }

    public Mono<Permission> insert(Permission permission) {
        PermissionId id = permission.getId();
        return db.sql("INSERT INTO PERMISSION (APP_NAME, EMPLOYEE_NUMBER, SYSTEM, CONNECTOR, ROLE) "
                        + "VALUES (:appName, :emp, :system, :connector, :role)")
                .bind("appName", id.appName())
                .bind("emp", id.employeeNumber())
                .bind("system", id.system())
                .bind("connector", id.connector())
                .bind("role", permission.getRole())
                .fetch().rowsUpdated()
                .then(findById(id));
    }

    public Mono<Permission> updateRole(PermissionId id, String role) {
        return db.sql("UPDATE PERMISSION SET ROLE = :role "
                        + "WHERE APP_NAME = :appName AND EMPLOYEE_NUMBER = :emp "
                        + "AND SYSTEM = :system AND CONNECTOR = :connector")
                .bind("role", role)
                .bind("appName", id.appName())
                .bind("emp", id.employeeNumber())
                .bind("system", id.system())
                .bind("connector", id.connector())
                .fetch().rowsUpdated()
                .then(findById(id));
    }

    public Mono<Void> deleteById(PermissionId id) {
        return db.sql("DELETE FROM PERMISSION "
                        + "WHERE APP_NAME = :appName AND EMPLOYEE_NUMBER = :emp "
                        + "AND SYSTEM = :system AND CONNECTOR = :connector")
                .bind("appName", id.appName())
                .bind("emp", id.employeeNumber())
                .bind("system", id.system())
                .bind("connector", id.connector())
                .fetch().rowsUpdated()
                .then();
    }

    private static BiFunction<Row, RowMetadata, Permission> rowMapper() {
        return (row, meta) -> new Permission(
                new PermissionId(
                        row.get("APP_NAME", String.class),
                        row.get("EMPLOYEE_NUMBER", String.class),
                        row.get("SYSTEM", String.class),
                        row.get("CONNECTOR", String.class)),
                row.get("ROLE", String.class),
                row.get("CREATED_AT", LocalDateTime.class));
    }
}
