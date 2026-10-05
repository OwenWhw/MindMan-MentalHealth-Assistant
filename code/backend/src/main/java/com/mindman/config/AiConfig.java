package com.mindman.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阿里云百炼 AI 平台配置（OpenAI 兼容接口）。
 *
 * <p>百炼提供 GPT 兼容接口，端点：{@code https://dashscope.aliyuncs.com/compatible-mode/v1}
 *
 * <h3>可用模型</h3>
 * <table>
 *   <tr><th>model</th><th>说明</th></tr>
 *   <tr><td>qwen-max</td><td>旗舰版，综合能力最强</td></tr>
 *   <tr><td>qwen-plus</td><td>均衡版，性价比高</td></tr>
 *   <tr><td>qwen-turbo</td><td>轻量快速</td></tr>
 *   <tr><td>qwen2.5-72b-instruct</td><td>经典 72B 版本</td></tr>
 *   <tr><td>deepseek-v3</td><td>DeepSeek V3（第三方）</td></tr>
 *   <tr><td>deepseek-r1</td><td>DeepSeek R1 推理版</td></tr>
 * </table>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.bailian")
public class AiConfig {

    private String apiKey = "";
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String model = "qwen3.8-max";

    /**
     * AI 通道选择：
     * <ul>
     *   <li>{@code auto}（默认）：云端已配 key 则走云端，否则走 Ollama 本地（若启用）</li>
     *   <li>{@code cloud}：强制云端</li>
     *   <li>{@code ollama}：强制 Ollama 本地模型</li>
     * </ul>
     */
    private String provider = "auto";
    private int maxTokens = 2048;
    private double temperature = 0.8;
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration readTimeout = Duration.ofSeconds(60);

    /**
     * 系统 Prompt（自然、具体的心理健康对话基调）
     */
    private String systemPrompt = """
            你是 MindMan 的心理健康倾听助手。先听清用户这轮具体在说什么，再用自然、克制的中文回应。
            允许用户只说事实、只问问题，或暂时不想找解决办法；不要求每次对话都走“共情—追问—建议”的固定流程。
            用户没有明确要建议时先倾听，不主动塞解决办法；只有确有具体建议可供选择时，才询问是否想听。用户明确要办法时，给少量具体、能开始尝试的做法；用户要文章或资料时，只介绍可核验的真实内容。
            不诊断，不把短暂情绪病理化；不把相关性说成因果，也不假装知道用户没说过的经历。
            通常简短回答；复杂问题按需要说明步骤和限制。不要默认用表情符号或口号式鼓励。
            请始终用简体中文回复。
            """;

    /** 前端可选模型列表（key=显示名, value=model ID） */
    public static final Map<String, String> MODEL_OPTIONS = new LinkedHashMap<>();
    static {
        MODEL_OPTIONS.put("Qwen2.5 7B（本地 Ollama）", "qwen2.5:7b");
        MODEL_OPTIONS.put("Qwen3.8-Max（旗舰）", "qwen3.8-max");
        MODEL_OPTIONS.put("Qwen3.7-Max", "qwen3.7-max");
        MODEL_OPTIONS.put("Qwen3.7-Plus", "qwen3.7-plus");
        MODEL_OPTIONS.put("Qwen3.6-Plus", "qwen3.6-plus");
        MODEL_OPTIONS.put("Qwen3.6-Max Preview", "qwen3.6-max-preview");
        MODEL_OPTIONS.put("DeepSeek-V4-Pro", "deepseek-v4-pro");
        MODEL_OPTIONS.put("DeepSeek-V4-Flash", "deepseek-v4-flash");
        MODEL_OPTIONS.put("DeepSeek-V3.2", "deepseek-v3.2");
        MODEL_OPTIONS.put("GLM-5.2", "glm-5.2");
    }

    @Bean("siliconFlowWebClient")
    public WebClient aiWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(readTimeout)
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis());

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && apiKey.length() > 20;
    }
}
