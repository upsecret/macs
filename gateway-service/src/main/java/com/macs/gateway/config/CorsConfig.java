package com.macs.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS 정책: 모든 origin / method / header 허용.
 *
 * <p>credentials 를 허용하므로 {@code allowedOrigins="*"} 는 사용 불가.
 * 대신 {@code allowedOriginPatterns="*"} 를 쓰면 어떤 origin 이든 와일드카드 매칭으로 통과시키면서
 * 응답 헤더의 {@code Access-Control-Allow-Origin} 은 요청 origin 으로 echo back 한다.
 *
 * <p>운영 전환 시 이 파일을 좁혀야 함 — README §9 TODO 참조.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
