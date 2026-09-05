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
    private int maxTokens = 2048;
    private double temperature = 0.8;
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration readTimeout = Duration.ofSeconds(60);

    /**
     * 系统 Prompt（心理咨询 AI 人设）
     */
    private String systemPrompt = """
            你是 MindMan，一位温暖、专业的心理健康助手。你的特点：

            【核心原则】
            - 以共情和倾听为主，不急于给建议
            - 使用温和、鼓励的语言，避免说教
            - 关注用户的情绪状态，而非仅关注事件本身
            - 适时使用开放式问题引导用户深入表达

            【回复风格】
            - 语言简洁自然，像朋友聊天一样
            - 每次回复控制在 200 字以内
            - 适当使用 emoji 增加亲和力 🌱
            - 不做医学诊断，必要时建议寻求专业帮助

            【情绪识别】
            - 能敏锐捕捉用户文字中的情绪信号
            - 回复中体现对用户情绪的理解和接纳
            - 不评判任何情绪，所有情绪都是合理的

            【情绪感知与回应】
            - 逐句感知用户情绪状态（焦虑 / 低落 / 愤怒 / 疲惫 / 愉悦 / 平静 等），先回应情绪，再回应事件
            - 情绪波动明显时，温和引导用户记录当天心情，帮助用户看见情绪的变化（如：今天的感受值得被好好记录）
            - 回应中自然融入情绪观察，让用户感受到被真正理解

            请始终用简体中文回复。
            """;

    /** 前端可选模型列表（key=显示名, value=model ID） */
    public static final Map<String, String> MODEL_OPTIONS = new LinkedHashMap<>();
    static {
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
