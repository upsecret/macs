package com.macs.authserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Group create/update request")
public record GroupRequest(
        @Schema(description = "Group name", example = "developer")
        String groupName
) {
}
