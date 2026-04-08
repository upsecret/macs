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
import java.util.List;

@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final ProxyManager<byte[]> proxyManager;
    private final BucketConfiguration defaultConfig;
    private final List<RateLimitProperties.Override> overrides;

    public RateLimitFilter(ProxyManager<byte[]> proxyManager, RateLimitProperties props) {
        this.proxyManager = proxyManager;
        this.overrides = props.overrides();

        // 기본: refillGreedy (연속 충전)
        this.defaultConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(props.capacity())
                        .refillGreedy(props.refillTokens(), props.refillDuration())
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

        // 경로별 오버라이드 매칭
        RateLimitProperties.Override matched = findOverride(path);
        BucketConfiguration config = matched != null ? buildOverrideConfig(matched) : defaultConfig;

        // 오버라이드 경로는 별도 버킷 키 사용 (기본 버킷과 분리)
        String key = matched != null
                ? matched.pathPrefix() + ":" + appName + ":" + employeeNumber
                : appName + ":" + employeeNumber;
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);

        return Mono.fromCallable(() -> {
                    var bucket = proxyManager.builder().build(keyBytes, () -> config);
                    return bucket.tryConsumeAndReturnRemaining(1);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(probe -> handleProbe(probe, exchange, chain, matched));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }

    /** 경로 prefix 매칭 */
    private RateLimitProperties.Override findOverride(String path) {
        for (var o : overrides) {
            if (path.startsWith(o.pathPrefix())) return o;
        }
        return null;
    }

    /** 오버라이드: refillIntervally (일괄 충전 — 초과 시 대기 후 한번에 복원) */
    private BucketConfiguration buildOverrideConfig(RateLimitProperties.Override o) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(o.capacity())
                        .refillIntervally(o.refillTokens(), o.refillDuration())
                        .build())
                .build();
    }

    private Mono<Void> handleProbe(ConsumptionProbe probe,
                                   ServerWebExchange exchange,
                                   GatewayFilterChain chain,
                                   RateLimitProperties.Override matched) {
        if (probe.isConsumed()) {
            exchange.getResponse().getHeaders()
                    .add("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            return chain.filter(exchange);
        }

        long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
        exchange.getResponse().getHeaders()
                .add("Retry-After", String.valueOf(retryAfterSeconds));

        String msg = matched != null
                ? "Rate limit exceeded for " + matched.pathPrefix() + " (limit: "
                  + matched.capacity() + "/" + matched.refillDuration().getSeconds() + "s)"
                : "Rate limit exceeded";

        return HeaderValidationFilter.writeError(
                exchange, HttpStatus.TOO_MANY_REQUESTS, msg);
    }
}
