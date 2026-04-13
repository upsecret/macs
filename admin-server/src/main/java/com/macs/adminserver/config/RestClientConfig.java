package com.macs.adminserver.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    /**
     * Connector 의 OpenAPI 문서를 원격에서 가져올 때 사용.
     * 외부 endpoint 일 수도 있어 연결 타임아웃을 넉넉히 주지 말고, 빠르게 실패하도록.
     */
    @Bean
    public RestClient apiDocsClient(RestClientCustomizer... customizers) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());

        RestClient.Builder builder = RestClient.builder().requestFactory(factory);
        for (RestClientCustomizer c : customizers) {
            c.customize(builder);
        }
        return builder.build();
    }
}
