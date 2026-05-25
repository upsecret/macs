package com.macs.authserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient adminServerWebClient() {
        // admin-server merged into gateway-service (PR 5). Permission lookup endpoint
        // /api/admin/permissions/users/{app}/{emp} 는 이제 gateway 의 local @RestController 가 응답.
        return WebClient.builder()
                .baseUrl("http://gateway-service:8080")
                .build();
    }
}
