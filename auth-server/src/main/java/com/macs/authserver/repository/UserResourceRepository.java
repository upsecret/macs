package com.macs.authserver.repository;

import com.macs.authserver.domain.UserResource;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class UserResourceRepository {

    private final DatabaseClient client;

    public UserResourceRepository(DatabaseClient client) {
        this.client = client;
    }

    public Flux<UserResource> findByEmployeeNumberAndSystemName(String employeeNumber, String systemName) {
        return client.sql("SELECT EMPLOYEE_NUMBER, SYSTEM_NAME, RESOURCE_NAME FROM USER_RESOURCE WHERE EMPLOYEE_NUMBER = :emp AND SYSTEM_NAME = :sys")
                .bind("emp", employeeNumber).bind("sys", systemName)
                .map((row, meta) -> new UserResource(row.get("EMPLOYEE_NUMBER", String.class), row.get("SYSTEM_NAME", String.class), row.get("RESOURCE_NAME", String.class)))
                .all();
    }

    public Mono<Void> insert(String employeeNumber, String systemName, String resourceName) {
        return client.sql("INSERT INTO USER_RESOURCE (EMPLOYEE_NUMBER, SYSTEM_NAME, RESOURCE_NAME) VALUES (:emp, :sys, :res)")
                .bind("emp", employeeNumber).bind("sys", systemName).bind("res", resourceName).then();
    }

    public Mono<Void> delete(String employeeNumber, String systemName, String resourceName) {
        return client.sql("DELETE FROM USER_RESOURCE WHERE EMPLOYEE_NUMBER = :emp AND SYSTEM_NAME = :sys AND RESOURCE_NAME = :res")
                .bind("emp", employeeNumber).bind("sys", systemName).bind("res", resourceName).then();
    }
}
