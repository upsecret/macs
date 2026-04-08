package com.macs.configserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Config property response")
public record PropertyResponse(
        String application,
        String profile,
        String label,
        String propKey,
        String propValue
) {
}
