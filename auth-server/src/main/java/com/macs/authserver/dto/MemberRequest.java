package com.macs.authserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Group member request")
public record MemberRequest(
        @Schema(description = "Employee number", example = "EMP005")
        String employeeNumber
) {
}
