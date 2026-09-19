package com.mindman.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Ollama 本地模型配置。
 *
 * <p>Ollama 安装后默认监听 {@code http://localhost:11434}，
 * 其 {@code /v1} 端点兼容 OpenAI 协议，无需 API Key。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.ollama")
public class OllamaProperties {

    /** 是否启用 Ollama 本地通道 */
    private boolean enabled = false;

    /** Ollama OpenAI 兼容端点 */
    private String baseUrl = "http://localhost:11434/v1";

    /** 默认本地模型（ollama pull 后的名称，如 qwen2.5:7b） */
    private String model = "qwen2.5:7b";

    private int maxTokens = 2048;
    private double temperature = 0.8;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(120);
}
