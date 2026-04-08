package com.macs.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "gateway.rate-limit")
public record RateLimitProperties(
        long capacity,
        long refillTokens,
        Duration refillDuration,
        List<Override> overrides
) {
    /** 경로별 유량 제어 오버라이드 */
    public record Override(
            String pathPrefix,
            long capacity,
            long refillTokens,
            Duration refillDuration
    ) {}

    public RateLimitProperties {
        if (overrides == null) overrides = List.of();
    }
}
