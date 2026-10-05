package com.mindman.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindman.config.AiConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流式对话客户端路由器。
 *
 * <p>管理云端（百炼/硅基流动，OpenAI 兼容）与本地（Ollama，OpenAI 兼容 /v1）两条通道，
 * 按 {@code ai.provider}（auto / cloud / ollama）自动选择：
 *
 * <ul>
 *   <li>{@code auto}：云端已配置 API Key → 云端；否则 Ollama 已启用 → Ollama；都没有 → 明确报告通道不可用</li>
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

    @Autowired
    public ChatClientRouter(AiConfig config, OllamaProperties ollamaProps, ObjectMapper objectMapper) {
        this(config, ollamaProps,
                new OpenAiCompatStreamingChatClient(
                        "cloud", config.getBaseUrl(), config.getApiKey(), config.getModel(),
                        config.getMaxTokens(), config.getTemperature(),
                        config.getConnectTimeout(), config.getReadTimeout(), objectMapper),
                new OpenAiCompatStreamingChatClient(
                        "ollama", ollamaProps.getBaseUrl(), "ollama", ollamaProps.getModel(),
                        ollamaProps.getMaxTokens(), ollamaProps.getTemperature(),
                        ollamaProps.getConnectTimeout(), ollamaProps.getReadTimeout(), objectMapper));
    }

    ChatClientRouter(AiConfig config, OllamaProperties ollamaProps,
                     StreamingChatClient cloudClient, StreamingChatClient ollamaClient) {
        this.config = config;
        this.ollamaProps = ollamaProps;
        this.cloudClient = cloudClient;
        this.ollamaClient = ollamaClient;
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

    /** 根据用户显式选择的本地模型决定路由；其余情况遵循全局 provider 配置。 */
    private StreamingChatClient current(ChatOptions options) {
        if (options != null && ollamaProps.getModel().equalsIgnoreCase(options.getModel())
                && autoProvider() && isOllamaAvailable()) {
            return ollamaClient;
        }
        return current();
    }

    /** 将模型选项转换为对应通道可识别的模型名。 */
    private ChatOptions optionsFor(StreamingChatClient client, ChatOptions options) {
        String model = options == null ? null : options.getModel();
        if ("ollama".equals(client.name())) model = ollamaProps.getModel();
        else if (model != null && model.equalsIgnoreCase(ollamaProps.getModel())) model = config.getModel();
        return ChatOptions.builder()
                .model(model)
                .maxTokens(options == null ? null : options.getMaxTokens())
                .temperature(options == null ? null : options.getTemperature())
                .build();
    }

    private boolean autoProvider() {
        return config.getProvider() == null || "auto".equalsIgnoreCase(config.getProvider());
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
        m.put("current", cur == null ? "unavailable" : cur.name());
        return m;
    }

    /** 快捷流式调用；auto 模式在首个 token 前发生通道错误时切换到另一通道。 */
    public Flux<String> stream(List<ChatMessage> messages, ChatOptions options) {
        StreamingChatClient client = current(options);
        if (client == null) {
            return Flux.error(new IllegalStateException("无可用 AI 通道"));
        }
        Flux<String> primary = client.stream(messages, optionsFor(client, options));
        if (!autoProvider()) return primary;

        StreamingChatClient backup = "ollama".equals(client.name())
                ? (cloudClient.isAvailable() ? cloudClient : null)
                : (isOllamaAvailable() ? ollamaClient : null);
        if (backup == null) return primary;

        AtomicBoolean emitted = new AtomicBoolean(false);
        return primary.doOnNext(ignored -> emitted.set(true)).onErrorResume(error -> {
            if (emitted.get()) return Flux.error(error);
            log.warn("[ChatClientRouter] {} 通道调用失败（{}），改用 {} 通道",
                    client.name(), error.getMessage(), backup.name());
            return backup.stream(messages, optionsFor(backup, options));
        });
    }

    /** 快捷同步调用；auto 模式下首选通道失败时切换到另一可用通道。 */
    public String call(List<ChatMessage> messages, ChatOptions options) {
        StreamingChatClient client = current(options);
        if (client == null) {
            throw new IllegalStateException("无可用 AI 通道");
        }
        try {
            return client.call(messages, optionsFor(client, options));
        } catch (Exception e) {
            if (!autoProvider()) throw e;
            StreamingChatClient backup = "ollama".equals(client.name())
                    ? (cloudClient.isAvailable() ? cloudClient : null)
                    : (isOllamaAvailable() ? ollamaClient : null);
            if (backup == null) throw e;
            log.warn("[ChatClientRouter] {} 通道调用失败（{}），改用 {} 通道",
                    client.name(), e.getMessage(), backup.name());
            return backup.call(messages, optionsFor(backup, options));
        }
    }
}
