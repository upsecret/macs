package com.macs.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class KeyResolverConfig {

    /**
     * 기본 KeyResolver: app_name + employee_number 헤더 기반.
     * RequestRateLimiter 에서 #{@headerKeyResolver} 로 참조.
     */
    @Bean
    public KeyResolver headerKeyResolver() {
        return exchange -> {
            String app = exchange.getRequest().getHeaders().getFirst("app_name");
            String emp = exchange.getRequest().getHeaders().getFirst("employee_number");
            if (app != null && emp != null) {
                return Mono.just(app + ":" + emp);
            }
            return Mono.just("anonymous");
        };
    }
}
