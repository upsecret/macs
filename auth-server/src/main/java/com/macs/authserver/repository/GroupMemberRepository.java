package com.macs.authserver.repository;

import com.macs.authserver.domain.GroupMember;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class GroupMemberRepository {

    private final DatabaseClient client;

    public GroupMemberRepository(DatabaseClient client) {
        this.client = client;
    }

    public Flux<GroupMember> findByGroupId(String groupId) {
        return client.sql("SELECT GROUP_ID, EMPLOYEE_NUMBER FROM GROUP_MEMBER WHERE GROUP_ID = :groupId")
                .bind("groupId", groupId)
                .map((row, meta) -> new GroupMember(
                        row.get("GROUP_ID", String.class),
                        row.get("EMPLOYEE_NUMBER", String.class)))
                .all();
    }

    public Mono<Void> insert(String groupId, String employeeNumber) {
        return client.sql("INSERT INTO GROUP_MEMBER (GROUP_ID, EMPLOYEE_NUMBER) VALUES (:groupId, :employeeNumber)")
                .bind("groupId", groupId)
                .bind("employeeNumber", employeeNumber)
                .then();
    }

    public Mono<Void> deleteByGroupIdAndEmployeeNumber(String groupId, String employeeNumber) {
        return client.sql("DELETE FROM GROUP_MEMBER WHERE GROUP_ID = :groupId AND EMPLOYEE_NUMBER = :employeeNumber")
                .bind("groupId", groupId)
                .bind("employeeNumber", employeeNumber)
                .then();
    }
}
