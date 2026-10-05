package com.mindman.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindman.dto.GardenInsightRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PythonGardenAgentClientTest {

    @Test
    void sendsTheCurrentRecordAndTokenToThePythonEndpoint() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> requestToken = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/garden/insight", exchange -> {
            requestToken.set(exchange.getRequestHeaders().getFirst("X-Agent-Token"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"source\":\"agent\",\"evidence\":\"开会时很紧张\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        try {
            ObjectMapper mapper = new ObjectMapper();
            PythonGardenAgentClient client = new PythonGardenAgentClient(true,
                    "http://127.0.0.1:" + server.getAddress().getPort(), "internal-test-token", 3, mapper);
            GardenInsightRequest request = new GardenInsightRequest();
            request.setEmotion("焦虑");
            request.setContent("开会时很紧张，后来慢慢平静了。");
            request.setTrigger("工作");

            var result = client.analyze(request);

            assertNotNull(result);
            assertEquals("agent", result.path("source").asText());
            assertEquals("internal-test-token", requestToken.get());
            var sent = mapper.readTree(requestBody.get());
            assertEquals("焦虑", sent.path("emotion").asText());
            assertEquals(request.getContent(), sent.path("content").asText());
            assertEquals("工作", sent.path("trigger").asText());
            assertEquals(3, sent.size());
        } finally {
            server.stop(0);
        }
    }
}
