package com.macs.gateway.filter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reactivestreams.Publisher;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Global request/response logging filter.
 *
 * <p>모든 inbound HTTP 요청 한 건당 최소 2줄을 남긴다:
 * <ul>
 *   <li>{@code >>> METHOD URI  route=... peer=... app=... emp=... req-bytes=... ct=...}</li>
 *   <li>{@code <<< STATUS  duration=Xms  resp-bytes=... ct=...}</li>
 * </ul>
 * 그리고 설정에 따라 선택적으로 request/response headers + body 를 추가 로깅한다.
 *
 * <p>특징:
 * <ul>
 *   <li>OTel trace_id 가 이미 log4j2 JSON layout 에 포함돼 있어 correlation 은 그걸로 해결.</li>
 *   <li>Authorization / Cookie / Set-Cookie / Proxy-Authorization 는 자동 마스킹.</li>
 *   <li>바이너리 Content-Type (image/*, video/*, application/octet-stream 등) 은 body 로깅 건너뜀.</li>
 *   <li>{@code macs.gateway.logging.skip-actuator=true} 이면 {@code /actuator/**} 는 통째로 건너뜀.</li>
 *   <li>chain 에서 예외 발생 시 duration + 실패 사유도 기록.</li>
 * </ul>
 */
@Component
public class RequestResponseLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LogManager.getLogger(RequestResponseLoggingFilter.class);

    private static final String START_NS_ATTR = "macs.logging.startNs";

    private static final Set<String> MASKED_HEADERS = Set.of(
            "authorization",
            "cookie",
            "set-cookie",
            "proxy-authorization"
    );

    // 텍스트 계열 Content-Type 접두사. 이 외에는 body 로깅을 스킵한다.
    private static final List<String> TEXT_CONTENT_PREFIXES = List.of(
            "application/json",
            "application/xml",
            "application/x-www-form-urlencoded",
            "application/yaml",
            "application/x-ndjson",
            "text/"
    );

    @Value("${macs.gateway.logging.body:true}")
    private boolean logBody;

    @Value("${macs.gateway.logging.headers:true}")
    private boolean logHeaders;

    @Value("${macs.gateway.logging.max-body-size:10240}")
    private int maxBodySize;

    @Value("${macs.gateway.logging.skip-actuator:true}")
    private boolean skipActuator;

    @Override
    public int getOrder() {
        // HeaderValidationFilter(HIGHEST_PRECEDENCE) 뒤, AuthValidation 보다 앞.
        return Ordered.HIGHEST_PRECEDENCE + 3;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (skipActuator && path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        // MDC for downstream logs during this exchange
        String appName = firstHeader(request, "app_name");
        String employeeNumber = firstHeader(request, "employee_number");
        MDC.put("app_name", nullToDash(appName));
        MDC.put("employee_number", nullToDash(employeeNumber));

        exchange.getAttributes().put(START_NS_ATTR, System.nanoTime());

        // Request line
        logRequestLine(exchange, request);

        if (logHeaders) {
            logRequestHeaders(request);
        }

        ServerHttpResponseDecorator decoratedResponse = createResponseDecorator(exchange);

        // GET/HEAD/OPTIONS 은 body 조인 스킵 (일반적으로 body 없음 — 조인하면 불필요한 지연)
        HttpMethod m = request.getMethod();
        boolean mayHaveBody = HttpMethod.POST.equals(m)
                || HttpMethod.PUT.equals(m)
                || HttpMethod.PATCH.equals(m)
                || HttpMethod.DELETE.equals(m);

        if (!logBody || !mayHaveBody) {
            return chain.filter(exchange.mutate().response(decoratedResponse).build())
                    .doOnError(err -> logError(exchange, err))
                    .doFinally(signalType -> MDC.clear());
        }

        // Body 를 한 번 읽어 로깅 후 downstream 에 재공급
        return DataBufferUtils.join(request.getBody())
                .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    if (bytes.length > 0) {
                        logBody("REQUEST BODY",
                                request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE), bytes);
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
                .doOnError(err -> logError(exchange, err))
                .doFinally(signalType -> MDC.clear());
    }

    // ── Request logging ────────────────────────────────────────

    private void logRequestLine(ServerWebExchange exchange, ServerHttpRequest request) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = route != null ? route.getId() : "-";

        InetSocketAddress remote = request.getRemoteAddress();
        String peer = remote != null ? remote.getAddress().getHostAddress() : "-";

        HttpHeaders h = request.getHeaders();
        String contentType = h.getFirst(HttpHeaders.CONTENT_TYPE);
        long contentLength = h.getContentLength();
        String query = request.getURI().getRawQuery();

        log.info(">>> {} {}{}  route={}  peer={}  app={}  emp={}  req-bytes={}  ct={}",
                request.getMethod(),
                request.getURI().getPath(),
                query != null ? "?" + query : "",
                routeId,
                peer,
                nullToDash(firstHeader(request, "app_name")),
                nullToDash(firstHeader(request, "employee_number")),
                contentLength >= 0 ? contentLength : "-",
                contentType != null ? contentType : "-");
    }

    private void logRequestHeaders(ServerHttpRequest request) {
        request.getHeaders().forEach((name, values) ->
                log.info("    req-header {}: {}", name, maskIfSensitive(name, values)));
    }

    // ── Response logging ──────────────────────────────────────

    private ServerHttpResponseDecorator createResponseDecorator(ServerWebExchange exchange) {
        ServerHttpResponse original = exchange.getResponse();
        DataBufferFactory bufferFactory = original.bufferFactory();

        return new ServerHttpResponseDecorator(original) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (!logBody || !(body instanceof Flux<? extends DataBuffer> fluxBody)) {
                    logResponseSummary(exchange, -1);
                    if (logHeaders) {
                        logResponseHeaders(original);
                    }
                    return super.writeWith(body);
                }
                return super.writeWith(fluxBody.buffer().map(dataBuffers -> {
                    DataBuffer joined = bufferFactory.join(dataBuffers);
                    byte[] content = new byte[joined.readableByteCount()];
                    joined.read(content);
                    DataBufferUtils.release(joined);

                    logResponseSummary(exchange, content.length);
                    if (logHeaders) {
                        logResponseHeaders(original);
                    }
                    if (content.length > 0) {
                        logBody("RESPONSE BODY",
                                original.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE), content);
                    }
                    return bufferFactory.wrap(content);
                }));
            }
        };
    }

    private void logResponseSummary(ServerWebExchange exchange, int byteCount) {
        long startNs = exchange.getAttributeOrDefault(START_NS_ATTR, System.nanoTime());
        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
        ServerHttpResponse response = exchange.getResponse();
        log.info("<<< {}  duration={}ms  resp-bytes={}  ct={}",
                response.getStatusCode(),
                durationMs,
                byteCount >= 0 ? byteCount : "-",
                nullToDash(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)));
    }

    private void logResponseHeaders(ServerHttpResponse response) {
        response.getHeaders().forEach((name, values) ->
                log.info("    resp-header {}: {}", name, maskIfSensitive(name, values)));
    }

    // ── Error path ────────────────────────────────────────────

    private void logError(ServerWebExchange exchange, Throwable err) {
        long startNs = exchange.getAttributeOrDefault(START_NS_ATTR, System.nanoTime());
        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
        log.warn("<<< ERROR  duration={}ms  type={}  message={}",
                durationMs, err.getClass().getSimpleName(), err.getMessage());
    }

    // ── Helpers ───────────────────────────────────────────────

    private void logBody(String label, String contentType, byte[] content) {
        if (content.length == 0) return;
        if (!isTextContentType(contentType)) {
            log.info("{}: <{} bytes, binary ct={}>", label, content.length,
                    contentType != null ? contentType : "-");
            return;
        }
        String bodyText;
        if (content.length > maxBodySize) {
            bodyText = new String(content, 0, maxBodySize, StandardCharsets.UTF_8) + "...(truncated)";
        } else {
            bodyText = new String(content, StandardCharsets.UTF_8);
        }
        log.info("{}: {}", label, bodyText);
    }

    private boolean isTextContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) return false;
        try {
            MediaType mt = MediaType.parseMediaType(contentType);
            String full = (mt.getType() + "/" + mt.getSubtype()).toLowerCase(Locale.ROOT);
            return TEXT_CONTENT_PREFIXES.stream().anyMatch(full::startsWith);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static String maskIfSensitive(String name, List<String> values) {
        if (name != null && MASKED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
            return "***";
        }
        return String.join(", ", values);
    }

    private static String firstHeader(ServerHttpRequest request, String name) {
        return request.getHeaders().getFirst(name);
    }

    private static String nullToDash(String v) {
        return v == null || v.isBlank() ? "-" : v;
    }
}
