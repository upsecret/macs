package com.macs.gateway.admin.mcp.repository;

import com.macs.gateway.admin.mcp.domain.McpServer;
import io.r2dbc.spi.Parameter;
import io.r2dbc.spi.Parameters;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.function.BiFunction;

@Repository
public class McpServerRepository {

    private static final String COLS =
            "ID, NAME, DESCRIPTION, ENDPOINT_URL, TRANSPORT, AUTH_TYPE, AUTH_TOKEN, SYSTEM, CREATED_AT";

    private final DatabaseClient db;

    public McpServerRepository(DatabaseClient db) {
        this.db = db;
    }

    public Flux<McpServer> findAll() {
        return db.sql("SELECT " + COLS + " FROM MCP_SERVER")
                .map(rowMapper())
                .all();
    }

    public Mono<McpServer> findById(String id) {
        return db.sql("SELECT " + COLS + " FROM MCP_SERVER WHERE ID = :id")
                .bind("id", id)
                .map(rowMapper())
                .one();
    }

    public Mono<Boolean> existsById(String id) {
        return findById(id).hasElement();
    }

    public Mono<McpServer> insert(McpServer s) {
        return db.sql("INSERT INTO MCP_SERVER "
                        + "(ID, NAME, DESCRIPTION, ENDPOINT_URL, TRANSPORT, AUTH_TYPE, AUTH_TOKEN, SYSTEM) "
                        + "VALUES (:id, :name, :description, :endpoint, :transport, :authType, :authToken, :system)")
                .bind("id", s.getId())
                .bind("name", s.getName())
                .bind("endpoint", s.getEndpointUrl())
                .bind("transport", s.getTransport())
                .bind("authType", s.getAuthType())
                .bind("system", s.getSystem())
                .bind("description", nullSafe(s.getDescription()))
                .bind("authToken", nullSafe(s.getAuthToken()))
                .fetch().rowsUpdated()
                .then(findById(s.getId()));
    }

    public Mono<McpServer> update(McpServer s) {
        return db.sql("UPDATE MCP_SERVER SET NAME = :name, DESCRIPTION = :description, "
                        + "ENDPOINT_URL = :endpoint, TRANSPORT = :transport, "
                        + "AUTH_TYPE = :authType, AUTH_TOKEN = :authToken, SYSTEM = :system "
                        + "WHERE ID = :id")
                .bind("id", s.getId())
                .bind("name", s.getName())
                .bind("endpoint", s.getEndpointUrl())
                .bind("transport", s.getTransport())
                .bind("authType", s.getAuthType())
                .bind("system", s.getSystem())
                .bind("description", nullSafe(s.getDescription()))
                .bind("authToken", nullSafe(s.getAuthToken()))
                .fetch().rowsUpdated()
                .then(findById(s.getId()));
    }

    public Mono<Void> deleteById(String id) {
        return db.sql("DELETE FROM MCP_SERVER WHERE ID = :id")
                .bind("id", id)
                .fetch().rowsUpdated()
                .then();
    }

    private static BiFunction<Row, RowMetadata, McpServer> rowMapper() {
        return (row, meta) -> new McpServer(
                row.get("ID", String.class),
                row.get("NAME", String.class),
                row.get("DESCRIPTION", String.class),
                row.get("ENDPOINT_URL", String.class),
                row.get("TRANSPORT", String.class),
                row.get("AUTH_TYPE", String.class),
                row.get("AUTH_TOKEN", String.class),
                row.get("SYSTEM", String.class),
                row.get("CREATED_AT", LocalDateTime.class));
    }

    private static Parameter nullSafe(String v) {
        return v == null ? Parameters.in(String.class) : Parameters.in(v);
    }
}
