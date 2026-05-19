package com.macs.adminserver.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.macs.adminserver.mcp.client.McpJsonRpcClient;
import com.macs.adminserver.mcp.domain.McpServer;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * McpJsonRpcClient 단위/통합 테스트.
 *
 * 외부 의존성 없이 JDK 내장 com.sun.net.httpserver.HttpServer 를 띄워
 * MCP JSON-RPC 서버를 흉내내고, 클라이언트의 initialize → tools/list → tools/call
 * 호출 흐름을 종단 검증한다.
 */
class McpJsonRpcClientTest {

    private HttpServer httpServer;
    private McpJsonRpcClient client;
    private McpServer testServer;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private final AtomicReference<String> lastAccept = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/mcp", exchange -> {
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastAccept.set(exchange.getRequestHeaders().getFirst("Accept"));

            String body;
            try (InputStream in = exchange.getRequestBody()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonNode rpc = mapper.readTree(body);
            String method = rpc.path("method").asText();
            long id = rpc.path("id").asLong();

            ObjectNode resp = mapper.createObjectNode();
            resp.put("jsonrpc", "2.0");
            resp.put("id", id);

            switch (method) {
                case "initialize" -> {
                    ObjectNode result = resp.putObject("result");
                    result.put("protocolVersion", McpJsonRpcClient.PROTOCOL_VERSION);
                    result.putObject("capabilities").putObject("tools");
                    ObjectNode info = result.putObject("serverInfo");
                    info.put("name", "test-mcp");
                    info.put("version", "1.0.0");
                }
                case "tools/list" -> {
                    ObjectNode result = resp.putObject("result");
                    var tools = result.putArray("tools");
                    ObjectNode echo = tools.addObject();
                    echo.put("name", "echo");
                    echo.put("description", "echo test tool");
                    ObjectNode echoSchema = echo.putObject("inputSchema");
                    echoSchema.put("type", "object");
                    echoSchema.putObject("properties").putObject("message").put("type", "string");
                }
                case "tools/call" -> {
                    String name = rpc.path("params").path("name").asText();
                    if ("echo".equals(name)) {
                        String msg = rpc.path("params").path("arguments").path("message").asText();
                        ObjectNode result = resp.putObject("result");
                        ObjectNode block = result.putArray("content").addObject();
                        block.put("type", "text");
                        block.put("text", msg);
                    } else {
                        ObjectNode err = resp.putObject("error");
                        err.put("code", -32601);
                        err.put("message", "Unknown tool: " + name);
                    }
                }
                default -> {
                    ObjectNode err = resp.putObject("error");
                    err.put("code", -32601);
                    err.put("message", "Method not found: " + method);
                }
            }

            byte[] out = mapper.writeValueAsBytes(resp);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        httpServer.start();

        RestClient rc = RestClient.builder().build();
        client = new McpJsonRpcClient(rc, mapper);

        int port = httpServer.getAddress().getPort();
        testServer = new McpServer(
                "test", "Test MCP", null,
                "http://127.0.0.1:" + port + "/mcp",
                "streamable-http", "none", null, "common");
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) httpServer.stop(0);
    }

    @Test
    void initialize_returns_protocol_and_serverInfo() {
        JsonNode result = client.initialize(testServer);
        assertEquals(McpJsonRpcClient.PROTOCOL_VERSION, result.path("protocolVersion").asText());
        assertEquals("test-mcp", result.path("serverInfo").path("name").asText());
    }

    @Test
    void listTools_returns_array() {
        JsonNode result = client.listTools(testServer);
        JsonNode tools = result.get("tools");
        assertNotNull(tools);
        assertTrue(tools.isArray());
        assertEquals(1, tools.size());
        assertEquals("echo", tools.get(0).path("name").asText());
    }

    @Test
    void callTool_echo_returns_text_content() {
        ObjectNode args = mapper.createObjectNode();
        args.put("message", "hello from junit");
        JsonNode result = client.callTool(testServer, "echo", args);
        JsonNode content = result.get("content");
        assertNotNull(content);
        assertEquals(1, content.size());
        assertEquals("text", content.get(0).path("type").asText());
        assertEquals("hello from junit", content.get(0).path("text").asText());
    }

    @Test
    void callTool_unknown_throws_bad_gateway() {
        ObjectNode args = mapper.createObjectNode();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> client.callTool(testServer, "nonexistent", args));
        assertTrue(ex.getMessage().contains("Unknown tool"),
                "expected message to surface MCP error, got: " + ex.getMessage());
    }

    @Test
    void bearer_auth_header_is_set_when_configured() {
        McpServer authServer = new McpServer(
                "auth", "Auth MCP", null,
                testServer.getEndpointUrl(),
                "streamable-http", "bearer", "secret-xyz", "common");
        client.initialize(authServer);
        assertEquals("Bearer secret-xyz", lastAuthorization.get(),
                "Authorization header should be set with bearer token");
    }

    @Test
    void no_auth_means_no_authorization_header() {
        client.initialize(testServer);
        assertEquals(null, lastAuthorization.get(),
                "no auth config → no Authorization header");
    }

    @Test
    void accept_header_includes_json_and_sse() {
        client.initialize(testServer);
        String accept = lastAccept.get();
        assertNotNull(accept);
        assertTrue(accept.contains("application/json"), "Accept should advertise json: " + accept);
        assertTrue(accept.contains("text/event-stream"), "Accept should advertise SSE: " + accept);
    }
}
