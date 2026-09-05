package com.mindman.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindman.config.AiConfig;
import com.mindman.service.AiChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 硅基流动 AI 聊天服务实现。
 *
 * <h3>实现原理</h3>
 * <ul>
 *   <li>通过 {@link WebClient} 调用硅基流动 OpenAI 兼容接口（/v1/chat/completions）</li>
 *   <li><b>同步模式</b>：设置 stream=false，收集完整响应后返回字符串</li>
 *   <li><b>流式模式</b>：设置 stream=true，将 SSE 数据流转为 Flux&lt;String&gt;</li>
 *   <li>未配置 API Key 时自动降级为<b>本地模拟回复</b></li>
 * </ul>
 *
 * <h3>SSE 数据格式（硅基流动 / OpenAI 标准）</h3>
 * <pre>
 * data: {"choices":[{"delta":{"content":"你好"}}]}
 * data: {"choices":[{"delta":{"content":"！"}}]}
 * data: [DONE]
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private final AiConfig config;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    // ======================== 同步模式 ========================

    @Override
    public String chatSync(String userMessage, String context, String model) {
        if (!config.isConfigured()) {
            log.warn("AI 未配置（API Key 为空），使用模拟回复");
            return generateMockReply(userMessage);
        }

        try {
            Map<String, Object> body = buildRequestBody(userMessage, context, false, model);

            String rawJson = webClient.post()
                    .uri("/chat/completions")
                    .body(BodyInserters.fromValue(body))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(config.getReadTimeout().plusSeconds(5));

            return extractContentFromResponse(rawJson);
        } catch (Exception e) {
            log.error("AI 同步调用失败: {}", e.getMessage(), e);
            return generateFallbackReply(userMessage);
        }
    }

    // ======================== 流式模式 ========================

    @Override
    public Flux<String> chatStream(String userMessage, String context, String model) {
        if (!config.isConfigured()) {
            log.info("AI 未配置，使用模拟流式回复");
            return mockStreamReply(userMessage);
        }

        try {
            Map<String, Object> body = buildRequestBody(userMessage, context, true, model);

            return webClient.post()
                    .uri("/chat/completions")
                    .body(BodyInserters.fromValue(body))
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .retrieve()
                    // 使用 Spring 的 SSE 解码器逐事件解析，data() 即每条 data 负载（JSON 或 [DONE]）
                    .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                    .mapNotNull(ServerSentEvent::data)
                    .takeUntil("[DONE]"::equals)             // 遇到 [DONE] 结束
                    .flatMap(this::parseDeltaFromSseData)     // 解析 delta.content
                    .doOnNext(chunk -> log.info("[AI-stream] chunk={}", chunk.length() > 60 ? chunk.substring(0, 60) + "..." : chunk))
                    .doOnComplete(() -> log.info("[AI-stream] 完成"))
                    .doOnError(e -> log.error("[AI-stream] 错误: {}", e.getMessage()))
                    .switchIfEmpty(Flux.error(new RuntimeException("dashscope 返回空流")))
                    .onErrorResume(e -> {
                        log.warn("AI 流式异常，降级为模拟回复: {}", e.getMessage());
                        return mockStreamReply(userMessage);
                    });
        } catch (Exception e) {
            log.error("AI 流式调用初始化失败: {}", e.getMessage());
            return mockStreamReply(userMessage);
        }
    }

    // ======================== 内部方法 ========================

    /**
     * 构建请求体（OpenAI 兼容格式）
     */
    private Map<String, Object> buildRequestBody(String userMessage, String context, boolean stream, String model) {
        List<Map<String, String>> messages = new ArrayList<>();

        // 系统提示词
        messages.add(Map.of("role", "system", "content", config.getSystemPrompt()));

        // 会话上下文（如果有）
        if (context != null && !context.isBlank()) {
            // 将上下文拆分为用户/AI交替消息，这里简化为一条 assistant 摘要
            messages.add(Map.of("role", "assistant",
                    "content", "以下是之前的对话摘要，请基于此继续对话：\n" + context));
        }

        // 当前用户消息
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> body = new HashMap<>();
        body.put("model", model != null && !model.isBlank() ? model : config.getModel());
        body.put("messages", messages);
        body.put("max_tokens", config.getMaxTokens());
        body.put("temperature", config.getTemperature());
        body.put("stream", stream);
        // 不使用 top_p，让 temperature 完全控制随机性
        return body;
    }

    /**
     * 从同步响应 JSON 中提取 content 文本
     */
    private String extractContentFromResponse(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                return stripThink(choices.get(0).path("message").path("content").asText(""));
            }
        } catch (Exception e) {
            log.warn("解析 AI 响应 JSON 失败: {}", e.getMessage());
        }
        return "";
    }

    /**
     * 从 SSE data 行解析 delta.content
     *
     * @param sseData 如: {"id":"...","choices":[{"delta":{"content":"你好"}}]}
     * @return Mono&lt;String&gt; 发出 delta 的内容片段；空 delta 则发出空 Mono
     */
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
            // 某些行可能不是有效 JSON（如 [DONE] 已被 takeUntil 过滤），忽略
        }
        return Mono.empty();
    }

    /**
     * 去掉模型回复中的 <think>...</think> 推理过程，只保留正式回答
     */
    private String stripThink(String content) {
        if (content == null) return "";
        return content.replaceAll("(?s)<think>.*?</think>", "").trim();
    }

    // ======================== 降级 / 模拟回复 ========================

    /**
     * 本地模拟回复（当 AI 未配置或调用失败时降级使用）
     */
    private String generateMockReply(String userMessage) {
        String lower = userMessage.toLowerCase();

        if (lower.contains("焦虑") || lower.contains("紧张") || lower.contains("担心")) {
            return "我听到了你的不安，这种感觉确实让人很难受 😔\n\n" +
                   "焦虑其实是身体在提醒我们关注某些重要的事情。你愿意跟我说说，最近是什么让你感到这么紧张吗？我会一直在这里听你说。";
        }
        if (lower.contains("难过") || lower.contains("伤心") || lower.contains("哭")) {
            return "谢谢你愿意把脆弱的一面分享给我 🤗\n\n" +
                   "难过的时候，允许自己好好哭一场其实是很重要的事。你不需要时刻都坚强。能告诉我，是什么触发了这些情绪吗？";
        }
        if (lower.contains("失眠") || lower.contains("睡不着") || lower.contains("睡眠")) {
            return "失眠真的让人很疲惫，我完全理解这种感受 🌙\n\n" +
                   "睡不着的时候越着急反而越清醒。你最近是不是有什么事情一直在心里放不下？我们可以一起聊聊。";
        }
        if (lower.contains("累") || lower.contains("疲惫") || lower.contains("压力")) {
            return "听起来你最近承担了很多，辛苦了 💪\n\n" +
                   "有时候\"停下来\"比\"继续前进\"更需要勇气。你上一次真正放松是什么时候？";
        }
        if (lower.contains("孤独") || lower.contains("孤单") || lower.contains("没人")) {
            return "孤独感是很沉重的，但请记住——你并不真的孤单 🫂\n\n" +
                   "你愿意跟我多说说那种感觉吗？有时候把孤独说出来，它就没那么可怕了。";
        }

        return "我听到了你的分享，感谢你愿意告诉我这些 ✨\n\n" +
               "能再多说说你现在的感受吗？我们可以一起慢慢梳理。你提到的事情，对你来说一定不容易。我在这里陪着你。";
    }

    /**
     * 降级回复（AI 调用异常时返回的友好提示）
     */
    private String generateFallbackReply(String userMessage) {
        return "抱歉，我刚才走神了 🙈\n\n" +
               "你能再跟我说一遍吗？我正在认真听呢。";
    }

    /**
     * 模拟流式回复（按字符逐段发出，模拟打字机效果）
     */
    private Flux<String> mockStreamReply(String userMessage) {
        String fullReply = generateMockReply(userMessage);
        // 每 1-3 个字符作为一个 chunk 模拟流式效果
        List<String> chunks = new ArrayList<>();
        int i = 0;
        while (i < fullReply.length()) {
            int len = Math.min(1 + (int) (Math.random() * 2), fullReply.length() - i);
            chunks.add(fullReply.substring(i, i + len));
            i += len;
        }

        return Flux.fromIterable(chunks)
                .delayElements(Duration.ofMillis(30 + (long) (Math.random() * 40)));
    }
}
