package com.macs.authserver.repository;

import com.macs.authserver.domain.SystemConnector;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class SystemConnectorRepository {

    private final DatabaseClient client;

    public SystemConnectorRepository(DatabaseClient client) {
        this.client = client;
    }

    public Flux<SystemConnector> findAll() {
        return client.sql("SELECT SYSTEM_NAME, CONNECTOR_NAME, DESCRIPTION FROM SYSTEM_CONNECTOR ORDER BY SYSTEM_NAME, CONNECTOR_NAME")
                .map((row, meta) -> new SystemConnector(
                        row.get("SYSTEM_NAME", String.class),
                        row.get("CONNECTOR_NAME", String.class),
                        row.get("DESCRIPTION", String.class)))
                .all();
    }

    public Flux<SystemConnector> findBySystemName(String systemName) {
        return client.sql("SELECT SYSTEM_NAME, CONNECTOR_NAME, DESCRIPTION FROM SYSTEM_CONNECTOR WHERE SYSTEM_NAME = :sys")
                .bind("sys", systemName)
                .map((row, meta) -> new SystemConnector(
                        row.get("SYSTEM_NAME", String.class),
                        row.get("CONNECTOR_NAME", String.class),
                        row.get("DESCRIPTION", String.class)))
                .all();
    }

    public Flux<String> findDistinctSystems() {
        return client.sql("SELECT DISTINCT SYSTEM_NAME FROM SYSTEM_CONNECTOR ORDER BY SYSTEM_NAME")
                .map((row, meta) -> row.get("SYSTEM_NAME", String.class))
                .all();
    }

    public Mono<Void> insert(String systemName, String connectorName, String description) {
        return client.sql("INSERT INTO SYSTEM_CONNECTOR (SYSTEM_NAME, CONNECTOR_NAME, DESCRIPTION) VALUES (:sys, :conn, :desc)")
                .bind("sys", systemName)
                .bind("conn", connectorName)
                .bind("desc", description)
                .then();
    }
}
