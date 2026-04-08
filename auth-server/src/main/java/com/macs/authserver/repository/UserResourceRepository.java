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

    public Flux<UserResource> findByEmployeeNumberAndAppId(String employeeNumber, String appId) {
        return client.sql("""
                        SELECT EMPLOYEE_NUMBER, APP_ID, RESOURCE_NAME
                        FROM USER_RESOURCE
                        WHERE EMPLOYEE_NUMBER = :employeeNumber AND APP_ID = :appId
                        """)
                .bind("employeeNumber", employeeNumber)
                .bind("appId", appId)
                .map((row, meta) -> new UserResource(
                        row.get("EMPLOYEE_NUMBER", String.class),
                        row.get("APP_ID", String.class),
                        row.get("RESOURCE_NAME", String.class)))
                .all();
    }

    public Mono<Void> insert(String employeeNumber, String appId, String resourceName) {
        return client.sql("""
                        INSERT INTO USER_RESOURCE (EMPLOYEE_NUMBER, APP_ID, RESOURCE_NAME)
                        VALUES (:employeeNumber, :appId, :resourceName)
                        """)
                .bind("employeeNumber", employeeNumber)
                .bind("appId", appId)
                .bind("resourceName", resourceName)
                .then();
    }

    public Mono<Void> deleteByEmployeeNumberAndAppIdAndResourceName(
            String employeeNumber, String appId, String resourceName) {
        return client.sql("""
                        DELETE FROM USER_RESOURCE
                        WHERE EMPLOYEE_NUMBER = :employeeNumber AND APP_ID = :appId AND RESOURCE_NAME = :resourceName
                        """)
                .bind("employeeNumber", employeeNumber)
                .bind("appId", appId)
                .bind("resourceName", resourceName)
                .then();
    }
}
