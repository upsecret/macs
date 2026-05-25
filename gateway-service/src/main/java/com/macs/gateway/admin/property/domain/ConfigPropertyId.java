package com.macs.gateway.admin.property.domain;

public record ConfigPropertyId(
        String application,
        String profile,
        String label,
        String propKey
) {
}
