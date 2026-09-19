package com.mindman.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindman.config.AiConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流式对话客户端路由器。
 *
 * <p>管理云端（百炼/硅基流动，OpenAI 兼容）与本地（Ollama，OpenAI 兼容 /v1）两条通道，
 * 按 {@code ai.provider}（auto / cloud / ollama）自动选择：
 *
 * <ul>
 *   <li>{@code auto}：云端已配置 API Key → 云端；否则 Ollama 已启用 → Ollama；都没有 → null（由上层降级为模拟回复）</li>
 *   <li>{@code cloud} / {@code ollama}：强制指定通道，不可用时返回 null 并告警</li>
 * </ul>
 */
@Slf4j
@Component
public class ChatClientRouter {

    @Getter
    private final StreamingChatClient cloudClient;
    @Getter
    private final StreamingChatClient ollamaClient;
    private final AiConfig config;
    private final OllamaProperties ollamaProps;

    public ChatClientRouter(AiConfig config, OllamaProperties ollamaProps, ObjectMapper objectMapper) {
        this.config = config;
        this.ollamaProps = ollamaProps;
        this.cloudClient = new OpenAiCompatStreamingChatClient(
                "cloud", config.getBaseUrl(), config.getApiKey(), config.getModel(),
                config.getMaxTokens(), config.getTemperature(),
                config.getConnectTimeout(), config.getReadTimeout(), objectMapper);
        this.ollamaClient = new OpenAiCompatStreamingChatClient(
                "ollama", ollamaProps.getBaseUrl(), "ollama", ollamaProps.getModel(),
                ollamaProps.getMaxTokens(), ollamaProps.getTemperature(),
                ollamaProps.getConnectTimeout(), ollamaProps.getReadTimeout(), objectMapper);
        log.info("[ChatClientRouter] provider={}, cloud可用={}, ollama启用={}",
                config.getProvider(), cloudClient.isAvailable(), ollamaProps.isEnabled());
    }

    /** Ollama 通道是否可用（开关 + 端点配置） */
    public boolean isOllamaAvailable() {
        return ollamaProps.isEnabled() && ollamaClient.isAvailable();
    }

    /** 当前应使用的客户端；全部不可用时返回 null（上层降级） */
    public StreamingChatClient current() {
        String provider = config.getProvider() == null ? "auto" : config.getProvider().toLowerCase();
        switch (provider) {
            case "cloud":
                if (cloudClient.isAvailable()) return cloudClient;
                log.warn("[ChatClientRouter] 强制 cloud 但 API Key 未配置，降级");
                return null;
            case "ollama":
                if (isOllamaAvailable()) return ollamaClient;
                log.warn("[ChatClientRouter] 强制 ollama 但未启用（ai.ollama.enabled=false），降级");
                return null;
            case "auto":
            default:
                if (cloudClient.isAvailable()) return cloudClient;
                if (isOllamaAvailable()) return ollamaClient;
                return null;
        }
    }

    /**
     * 通道诊断信息（供管理端接口展示）
     */
    public Map<String, Object> diagnostics() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provider", config.getProvider());
        m.put("cloud", Map.of(
                "available", cloudClient.isAvailable(),
                "baseUrl", config.getBaseUrl(),
                "model", config.getModel()));
        m.put("ollama", Map.of(
                "available", isOllamaAvailable(),
                "enabled", ollamaProps.isEnabled(),
                "baseUrl", ollamaProps.getBaseUrl(),
                "model", ollamaProps.getModel()));
        StreamingChatClient cur = current();
        m.put("current", cur == null ? "mock（全部不可用，降级模拟回复）" : cur.name());
        return m;
    }

    /** 快捷流式调用（走当前通道；auto 模式下云端失败自动切换 Ollama 兜底） */
    public Flux<String> stream(List<ChatMessage> messages, ChatOptions options) {
        StreamingChatClient client = current();
        if (client == null) {
            return Flux.error(new IllegalStateException("无可用 AI 通道"));
        }
        Flux<String> primary = client.stream(messages, options);
        // auto 模式：云端调用失败且 Ollama 可用 → 自动切换本地通道
        if ("cloud".equals(client.name()) && isOllamaAvailable()
                && (config.getProvider() == null || "auto".equalsIgnoreCase(config.getProvider()))) {
            primary = primary.onErrorResume(e -> {
                log.warn("[ChatClientRouter] 云端调用失败（{}），自动切换 Ollama 本地通道", e.getMessage());
                return ollamaClient.stream(messages, options);
            });
        }
        return primary;
    }

    /** 快捷同步调用（走当前通道；auto 模式下云端失败自动切换 Ollama 兜底） */
    public String call(List<ChatMessage> messages, ChatOptions options) {
        StreamingChatClient client = current();
        if (client == null) {
            throw new IllegalStateException("无可用 AI 通道");
        }
        try {
            return client.call(messages, options);
        } catch (Exception e) {
            if ("cloud".equals(client.name()) && isOllamaAvailable()
                    && (config.getProvider() == null || "auto".equalsIgnoreCase(config.getProvider()))) {
                log.warn("[ChatClientRouter] 云端调用失败（{}），自动切换 Ollama 本地通道", e.getMessage());
                return ollamaClient.call(messages, options);
            }
            throw e;
        }
    }
}
