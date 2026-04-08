package com.macs.gateway.filter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reactivestreams.Publisher;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class RequestResponseLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LogManager.getLogger(RequestResponseLoggingFilter.class);
    private static final int MAX_LOG_BODY_SIZE = 10_240; // 10 KB

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String appName = request.getHeaders().getFirst("app_name");
        String employeeNumber = request.getHeaders().getFirst("employee_number");

        MDC.put("app_name", appName != null ? appName : "-");
        MDC.put("employee_number", employeeNumber != null ? employeeNumber : "-");

        logRequestHeaders(request);

        ServerHttpResponseDecorator decoratedResponse = createResponseDecorator(exchange);

        return DataBufferUtils.join(request.getBody())
                .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    if (bytes.length > 0) {
                        logBody("REQUEST BODY", bytes);
                    }

                    ServerHttpRequestDecorator decoratedRequest = new ServerHttpRequestDecorator(request) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return Flux.just(exchange.getResponse().bufferFactory().wrap(bytes));
                        }
                    };

                    return chain.filter(exchange.mutate()
                            .request(decoratedRequest)
                            .response(decoratedResponse)
                            .build());
                })
                .doFinally(signalType -> MDC.clear());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 3;
    }

    private void logRequestHeaders(ServerHttpRequest request) {
        log.info(">>> {} {} | Content-Type: {} | app_name: {} | employee_number: {}",
                request.getMethod(),
                request.getURI(),
                request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE),
                request.getHeaders().getFirst("app_name"),
                request.getHeaders().getFirst("employee_number"));
    }

    private ServerHttpResponseDecorator createResponseDecorator(ServerWebExchange exchange) {
        ServerHttpResponse original = exchange.getResponse();
        DataBufferFactory bufferFactory = original.bufferFactory();

        return new ServerHttpResponseDecorator(original) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux<? extends DataBuffer> fluxBody) {
                    return super.writeWith(fluxBody.buffer().map(dataBuffers -> {
                        DataBuffer joined = bufferFactory.join(dataBuffers);
                        byte[] content = new byte[joined.readableByteCount()];
                        joined.read(content);
                        DataBufferUtils.release(joined);

                        log.info("<<< {} | Content-Type: {}",
                                original.getStatusCode(),
                                original.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
                        logBody("RESPONSE BODY", content);

                        return bufferFactory.wrap(content);
                    }));
                }
                return super.writeWith(body);
            }
        };
    }

    private void logBody(String label, byte[] content) {
        if (content.length == 0) return;

        String bodyText;
        if (content.length > MAX_LOG_BODY_SIZE) {
            bodyText = new String(content, 0, MAX_LOG_BODY_SIZE, StandardCharsets.UTF_8) + "...(truncated)";
        } else {
            bodyText = new String(content, StandardCharsets.UTF_8);
        }
        log.debug("{}: {}", label, bodyText);
    }
}
