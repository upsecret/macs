package com.macs.authserver.domain;

public record GroupMember(
        String groupId,
        String employeeNumber
) {
}
