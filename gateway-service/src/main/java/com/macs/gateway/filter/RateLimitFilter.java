package com.macs.gateway.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import com.macs.gateway.config.RateLimitProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;

@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final ProxyManager<byte[]> proxyManager;
    private final BucketConfiguration bucketConfig;

    public RateLimitFilter(ProxyManager<byte[]> proxyManager, RateLimitProperties properties) {
        this.proxyManager = proxyManager;
        this.bucketConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(properties.capacity())
                        .refillGreedy(properties.refillTokens(), properties.refillDuration())
                        .build())
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path.startsWith("/actuator") || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs") || path.startsWith("/webjars")) {
            return chain.filter(exchange);
        }

        String appName = exchange.getRequest().getHeaders().getFirst("app_name");
        String employeeNumber = exchange.getRequest().getHeaders().getFirst("employee_number");
        if (appName == null || employeeNumber == null) {
            return chain.filter(exchange);
        }

        String key = appName + ":" + employeeNumber;
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);

        return Mono.fromCallable(() -> {
                    var bucket = proxyManager.builder().build(keyBytes, () -> bucketConfig);
                    return bucket.tryConsumeAndReturnRemaining(1);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(probe -> handleProbe(probe, exchange, chain));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }

    private Mono<Void> handleProbe(ConsumptionProbe probe,
                                   ServerWebExchange exchange,
                                   GatewayFilterChain chain) {
        if (probe.isConsumed()) {
            exchange.getResponse().getHeaders()
                    .add("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            return chain.filter(exchange);
        }

        long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
        exchange.getResponse().getHeaders()
                .add("Retry-After", String.valueOf(retryAfterSeconds));
        return HeaderValidationFilter.writeError(
                exchange, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
    }
}
