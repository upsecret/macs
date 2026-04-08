package com.macs.authserver.repository;

import com.macs.authserver.domain.GroupResource;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class GroupResourceRepository {

    private final DatabaseClient client;

    public GroupResourceRepository(DatabaseClient client) {
        this.client = client;
    }

    public Flux<GroupResource> findByGroupId(Long groupId) {
        return client.sql("SELECT GROUP_ID, RESOURCE_NAME FROM GROUP_RESOURCE WHERE GROUP_ID = :gid")
                .bind("gid", groupId)
                .map((row, meta) -> new GroupResource(row.get("GROUP_ID", Long.class), row.get("RESOURCE_NAME", String.class)))
                .all();
    }

    public Mono<Void> insert(Long groupId, String resourceName) {
        return client.sql("INSERT INTO GROUP_RESOURCE (GROUP_ID, RESOURCE_NAME) VALUES (:gid, :res)")
                .bind("gid", groupId).bind("res", resourceName).then();
    }

    public Mono<Void> delete(Long groupId, String resourceName) {
        return client.sql("DELETE FROM GROUP_RESOURCE WHERE GROUP_ID = :gid AND RESOURCE_NAME = :res")
                .bind("gid", groupId).bind("res", resourceName).then();
    }
}
