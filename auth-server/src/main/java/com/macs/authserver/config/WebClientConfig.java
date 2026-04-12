package com.macs.authserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient adminServerWebClient() {
        return WebClient.builder()
                .baseUrl("http://admin-server:8888")
                .build();
    }
}
