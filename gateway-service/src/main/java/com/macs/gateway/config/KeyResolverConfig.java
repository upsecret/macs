package com.macs.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class KeyResolverConfig {

    /**
     * 기본 KeyResolver: Client-App + Employee-Number 헤더 기반.
     * RequestRateLimiter 에서 #{@headerKeyResolver} 로 참조.
     */
    @Bean
    public KeyResolver headerKeyResolver() {
        return exchange -> {
            String app = exchange.getRequest().getHeaders().getFirst("Client-App");
            String emp = exchange.getRequest().getHeaders().getFirst("Employee-Number");
            if (app != null && emp != null) {
                return Mono.just(app + ":" + emp);
            }
            return Mono.just("anonymous");
        };
    }
}
