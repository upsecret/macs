package com.macs.gateway.admin.property.repository;

import com.macs.gateway.admin.property.domain.ConfigProperty;
import com.macs.gateway.admin.property.domain.ConfigPropertyId;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.BiFunction;

@Repository
public class ConfigPropertyRepository {

    private static final String COLS = "APPLICATION, PROFILE, LABEL, PROP_KEY, PROP_VALUE";

    private final DatabaseClient db;

    public ConfigPropertyRepository(DatabaseClient db) {
        this.db = db;
    }

    public Flux<ConfigProperty> findByApplicationAndProfileAndLabel(
            String application, String profile, String label) {
        return db.sql("SELECT " + COLS + " FROM PROPERTIES "
                        + "WHERE APPLICATION = :app AND PROFILE = :profile AND LABEL = :label")
                .bind("app", application)
                .bind("profile", profile)
                .bind("label", label)
                .map(rowMapper())
                .all();
    }

    public Flux<ConfigProperty> findByKeyPattern(
            String application, String profile, String label, String keyPattern) {
        return db.sql("SELECT " + COLS + " FROM PROPERTIES "
                        + "WHERE APPLICATION = :app AND PROFILE = :profile AND LABEL = :label "
                        + "AND PROP_KEY LIKE :pattern")
                .bind("app", application)
                .bind("profile", profile)
                .bind("label", label)
                .bind("pattern", keyPattern)
                .map(rowMapper())
                .all();
    }

    public Mono<ConfigProperty> findById(ConfigPropertyId id) {
        return db.sql("SELECT " + COLS + " FROM PROPERTIES "
                        + "WHERE APPLICATION = :app AND PROFILE = :profile "
                        + "AND LABEL = :label AND PROP_KEY = :key")
                .bind("app", id.application())
                .bind("profile", id.profile())
                .bind("label", id.label())
                .bind("key", id.propKey())
                .map(rowMapper())
                .one();
    }

    public Mono<Boolean> existsById(ConfigPropertyId id) {
        return findById(id).hasElement();
    }

    /**
     * INSERT or UPDATE depending on existence. Used by saveRouteProperties() which doesn't
     * pre-check (saveRouteProperties always deletes the route's keys first).
     * For correctness in mixed call paths, do an UPSERT-style: try update, if 0 rows then insert.
     */
    public Mono<ConfigProperty> upsert(ConfigProperty prop) {
        ConfigPropertyId id = prop.getId();
        return db.sql("UPDATE PROPERTIES SET PROP_VALUE = :val "
                        + "WHERE APPLICATION = :app AND PROFILE = :profile "
                        + "AND LABEL = :label AND PROP_KEY = :key")
                .bind("val", prop.getPropValue())
                .bind("app", id.application())
                .bind("profile", id.profile())
                .bind("label", id.label())
                .bind("key", id.propKey())
                .fetch().rowsUpdated()
                .flatMap(updated -> {
                    if (updated > 0) return Mono.just(prop);
                    return db.sql("INSERT INTO PROPERTIES (APPLICATION, PROFILE, LABEL, PROP_KEY, PROP_VALUE) "
                                    + "VALUES (:app, :profile, :label, :key, :val)")
                            .bind("app", id.application())
                            .bind("profile", id.profile())
                            .bind("label", id.label())
                            .bind("key", id.propKey())
                            .bind("val", prop.getPropValue())
                            .fetch().rowsUpdated()
                            .thenReturn(prop);
                });
    }

    public Mono<Void> deleteById(ConfigPropertyId id) {
        return db.sql("DELETE FROM PROPERTIES "
                        + "WHERE APPLICATION = :app AND PROFILE = :profile "
                        + "AND LABEL = :label AND PROP_KEY = :key")
                .bind("app", id.application())
                .bind("profile", id.profile())
                .bind("label", id.label())
                .bind("key", id.propKey())
                .fetch().rowsUpdated()
                .then();
    }

    public Mono<Void> deleteByKeyPattern(
            String application, String profile, String label, String keyPattern) {
        return db.sql("DELETE FROM PROPERTIES "
                        + "WHERE APPLICATION = :app AND PROFILE = :profile "
                        + "AND LABEL = :label AND PROP_KEY LIKE :pattern")
                .bind("app", application)
                .bind("profile", profile)
                .bind("label", label)
                .bind("pattern", keyPattern)
                .fetch().rowsUpdated()
                .then();
    }

    private static BiFunction<Row, RowMetadata, ConfigProperty> rowMapper() {
        return (row, meta) -> new ConfigProperty(
                new ConfigPropertyId(
                        row.get("APPLICATION", String.class),
                        row.get("PROFILE", String.class),
                        row.get("LABEL", String.class),
                        row.get("PROP_KEY", String.class)),
                row.get("PROP_VALUE", String.class));
    }
}
