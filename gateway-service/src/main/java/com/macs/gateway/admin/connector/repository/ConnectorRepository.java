package com.macs.gateway.admin.connector.repository;

import com.macs.gateway.admin.connector.domain.Connector;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.function.BiFunction;

@Repository
public class ConnectorRepository {

    private static final String COLS = "ID, TITLE, DESCRIPTION, TYPE, SYSTEM, DOCS_URL, CREATED_AT";

    private final DatabaseClient db;

    public ConnectorRepository(DatabaseClient db) {
        this.db = db;
    }

    public Flux<Connector> findAll() {
        return db.sql("SELECT " + COLS + " FROM CONNECTOR")
                .map(rowMapper())
                .all();
    }

    public Mono<Connector> findById(String id) {
        return db.sql("SELECT " + COLS + " FROM CONNECTOR WHERE ID = :id")
                .bind("id", id)
                .map(rowMapper())
                .one();
    }

    public Mono<Boolean> existsById(String id) {
        return findById(id).hasElement();
    }

    public Mono<Connector> insert(Connector c) {
        return db.sql("INSERT INTO CONNECTOR (ID, TITLE, DESCRIPTION, TYPE, SYSTEM, DOCS_URL) "
                        + "VALUES (:id, :title, :description, :type, :system, :docsUrl)")
                .bind("id", c.getId())
                .bind("title", c.getTitle())
                .bind("type", c.getType())
                .bind("system", c.getSystem())
                .bind("description", nullSafe(c.getDescription()))
                .bind("docsUrl", nullSafe(c.getDocsUrl()))
                .fetch().rowsUpdated()
                .then(findById(c.getId()));
    }

    public Mono<Connector> update(Connector c) {
        return db.sql("UPDATE CONNECTOR SET TITLE = :title, DESCRIPTION = :description, "
                        + "TYPE = :type, SYSTEM = :system, DOCS_URL = :docsUrl WHERE ID = :id")
                .bind("id", c.getId())
                .bind("title", c.getTitle())
                .bind("type", c.getType())
                .bind("system", c.getSystem())
                .bind("description", nullSafe(c.getDescription()))
                .bind("docsUrl", nullSafe(c.getDocsUrl()))
                .fetch().rowsUpdated()
                .then(findById(c.getId()));
    }

    private static io.r2dbc.spi.Parameter nullSafe(String v) {
        return v == null
                ? io.r2dbc.spi.Parameters.in(String.class)
                : io.r2dbc.spi.Parameters.in(v);
    }

    public Mono<Void> deleteById(String id) {
        return db.sql("DELETE FROM CONNECTOR WHERE ID = :id")
                .bind("id", id)
                .fetch().rowsUpdated()
                .then();
    }

    private static BiFunction<Row, RowMetadata, Connector> rowMapper() {
        return (row, meta) -> new Connector(
                row.get("ID", String.class),
                row.get("TITLE", String.class),
                row.get("DESCRIPTION", String.class),
                row.get("TYPE", String.class),
                row.get("SYSTEM", String.class),
                row.get("DOCS_URL", String.class),
                row.get("CREATED_AT", LocalDateTime.class));
    }
}
