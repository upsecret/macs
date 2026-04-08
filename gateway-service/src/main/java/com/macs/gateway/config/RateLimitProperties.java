package com.macs.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "gateway.rate-limit")
public record RateLimitProperties(
        long capacity,
        long refillTokens,
        Duration refillDuration
) {
}
