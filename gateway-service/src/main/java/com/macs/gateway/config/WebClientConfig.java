package com.macs.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient authServiceWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl("http://auth-server:9000")
                .build();
    }
}
