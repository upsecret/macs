package com.macs.adminserver.connector.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Gateway route metadata for path transformation (predicate + strip prefix + rewrite rules)")
public record RouteMetadataResponse(
        @Schema(description = "Path predicate pattern (first Path= arg)", example = "/auth/**")
        String pathPredicate,

        @Schema(description = "StripPrefix filter value (segments to strip). null if absent.", example = "1")
        Integer stripPrefix,

        @Schema(description = "Ordered RewritePath rules applied in sequence.")
        List<RewriteRule> rewriteRules
) {
    @Schema(description = "RewritePath filter rule")
    public record RewriteRule(
            @Schema(description = "Regex applied to path", example = "/auth/(?<seg>.*)")
            String regexp,
            @Schema(description = "Replacement expression", example = "/${seg}")
            String replacement
    ) {
    }
}
