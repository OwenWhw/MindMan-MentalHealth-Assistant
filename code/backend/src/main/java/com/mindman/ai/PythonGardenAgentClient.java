package com.mindman.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindman.dto.GardenInsightRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Optional bridge to the small Python garden reflection service. */
@Slf4j
@Component
public class PythonGardenAgentClient {

    private final boolean enabled;
    private final URI endpoint;
    private final String token;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PythonGardenAgentClient(
            @Value("${mindman.python-agent.enabled:false}") boolean enabled,
            @Value("${mindman.python-agent.base-url:http://127.0.0.1:8091}") String baseUrl,
            @Value("${mindman.python-agent.token:}") String token,
            @Value("${mindman.python-agent.timeout-seconds:50}") int timeoutSeconds,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/v1/garden/insight");
        this.token = token;
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    /** Returns null when disabled or unavailable; the existing Java agent handles fallback. */
    public JsonNode analyze(GardenInsightRequest request) {
        if (!enabled) return null;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(request), StandardCharsets.UTF_8));
            if (token != null && !token.isBlank()) builder.header("X-Agent-Token", token);
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("Python garden agent returned HTTP {}", response.statusCode());
                return null;
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Python garden agent call interrupted");
            return null;
        } catch (Exception e) {
            log.warn("Python garden agent unavailable: {}", e.getClass().getSimpleName());
            return null;
        }
    }
}
