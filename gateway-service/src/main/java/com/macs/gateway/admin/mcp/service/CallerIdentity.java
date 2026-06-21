package com.macs.gateway.admin.mcp.service;

import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

/**
 * MCP 프록시 호출자의 신원. McpController 가 들어온 요청에서 추출해
 * loopback 게이트웨이 라우트 호출에 그대로 전달한다. 라우트의
 * {@code HeaderValidationFilter}/{@code AuthValidation} 필터가 이 헤더로
 * 인증·인가를 수행한다.
 */
public record CallerIdentity(String authorization, String appName, String employeeNumber) {

    public static CallerIdentity from(ServerWebExchange exchange) {
        HttpHeaders h = exchange.getRequest().getHeaders();
        return new CallerIdentity(
                h.getFirst(HttpHeaders.AUTHORIZATION),
                h.getFirst("app_name"),
                h.getFirst("employee_number"));
    }
}
