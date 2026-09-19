package com.mindman.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议的 {@link StreamingChatClient} 通用实现。
 *
 * <p>百炼（DashScope compatible-mode）/ 硅基流动 / Ollama（/v1 端点）等
 * 均遵循同一协议，仅需替换 base-url / api-key / model 即可复用。
 *
 * <p>SSE 数据格式：
 * <pre>
 * data: {"choices":[{"delta":{"content":"你好"}}]}
 * data: [DONE]
 * </pre>
 */
@Slf4j
public class OpenAiCompatStreamingChatClient implements StreamingChatClient {

    private final String name;
    private final String baseUrl;
    private final String apiKey;
    private final String defaultModel;
    private final int maxTokens;
    private final double temperature;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public OpenAiCompatStreamingChatClient(String name, String baseUrl, String apiKey,
                                           String defaultModel, int maxTokens, double temperature,
                                           Duration connectTimeout, Duration readTimeout,
                                           ObjectMapper objectMapper) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.defaultModel = defaultModel;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(readTimeout)
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis());

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", "application/json");
        // Ollama 不校验 key，但协议上带 Bearer 头也无害；留空则不加
        if (!this.apiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + this.apiKey);
        }
        this.webClient = builder.build();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean isAvailable() {
        // 云端：api-key 已配置；Ollama：本地服务不校验 key，视为可用（由路由层做连通性探测）
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public Flux<String> stream(List<ChatMessage> messages, ChatOptions options) {
        Map<String, Object> body = buildRequestBody(messages, options, true);
        return webClient.post()
                .uri("/chat/completions")
                .body(BodyInserters.fromValue(body))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<org.springframework.http.codec.ServerSentEvent<String>>() {})
                .mapNotNull(org.springframework.http.codec.ServerSentEvent::data)
                .takeUntil("[DONE]"::equals)
                .flatMap(this::parseDeltaFromSseData)
                .doOnError(e -> log.error("[{}-stream] 调用错误: {}", name, e.getMessage()))
                .onErrorMap(e -> new RuntimeException("[" + name + "] AI 流式调用失败: " + e.getMessage(), e));
    }

    @Override
    public String call(List<ChatMessage> messages, ChatOptions options) {
        Map<String, Object> body = buildRequestBody(messages, options, false);
        String rawJson = webClient.post()
                .uri("/chat/completions")
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToMono(String.class)
                .block(readTimeout.plusSeconds(5));
        return extractContentFromResponse(rawJson);
    }

    // ======================== 内部方法 ========================

    private Map<String, Object> buildRequestBody(List<ChatMessage> messages, ChatOptions options, boolean stream) {
        List<Map<String, String>> msgList = new ArrayList<>();
        for (ChatMessage m : messages) {
            msgList.add(Map.of("role", m.role(), "content", m.content()));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", options != null && options.getModel() != null && !options.getModel().isBlank()
                ? options.getModel() : defaultModel);
        body.put("messages", msgList);
        body.put("max_tokens", options != null && options.getMaxTokens() != null ? options.getMaxTokens() : maxTokens);
        body.put("temperature", options != null && options.getTemperature() != null ? options.getTemperature() : temperature);
        body.put("stream", stream);
        return body;
    }

    /** 从 SSE data 行解析 delta.content，过滤空段与 &lt;think&gt; 推理段 */
    private Mono<String> parseDeltaFromSseData(String sseData) {
        try {
            JsonNode root = objectMapper.readTree(sseData);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                String content = choices.get(0).path("delta").path("content").asText("");
                if (!content.isEmpty()) {
                    return Mono.just(stripThink(content));
                }
            }
        } catch (Exception ignored) {
            // [DONE] 等非 JSON 行已在 takeUntil 处理，忽略解析失败
        }
        return Mono.empty();
    }

    private String extractContentFromResponse(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                return stripThink(choices.get(0).path("message").path("content").asText(""));
            }
        } catch (Exception e) {
            log.warn("[{}] 解析响应 JSON 失败: {}", name, e.getMessage());
        }
        return "";
    }

    /** 去掉推理模型输出的 &lt;think&gt;...&lt;/think&gt; 段 */
    private String stripThink(String content) {
        if (content == null) return "";
        return content.replaceAll("(?s)<think>.*?</think>", "").trim();
    }
}
