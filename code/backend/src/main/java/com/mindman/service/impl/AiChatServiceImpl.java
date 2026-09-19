package com.mindman.service.impl;

import com.mindman.ai.ChatClientRouter;
import com.mindman.ai.ChatMessage;
import com.mindman.ai.ChatOptions;
import com.mindman.ai.RagService;
import com.mindman.config.AiConfig;
import com.mindman.service.AiChatService;
import com.mindman.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 聊天服务实现（v2：经 {@link ChatClientRouter} 路由）。
 *
 * <h3>实现原理</h3>
 * <ul>
 *   <li>对话调用统一走 {@link ChatClientRouter}：自动在云端（百炼/硅基流动）
 *       与本地（Ollama OpenAI 兼容端点）之间选择通道，二者均为 SSE 流式</li>
 *   <li>系统提示词从<b>提示词模板服务</b>（prompt_template 表，scene=chat_system）动态获取，
 *       无生效模板时回退到 {@code ai.*.system-prompt} 内置配置</li>
 *   <li>全部通道不可用时自动降级为<b>本地模拟回复</b>（打字机式伪流式）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private final AiConfig config;
    private final ChatClientRouter chatClientRouter;
    private final PromptTemplateService promptTemplateService;
    private final RagService ragService;

    // ======================== 同步模式 ========================

    @Override
    public String chatSync(String userMessage, String context, String model) {
        if (chatClientRouter.current() == null) {
            log.warn("AI 通道不可用（云端未配 Key 且 Ollama 未启用），使用模拟回复");
            return generateMockReply(userMessage);
        }

        try {
            List<ChatMessage> messages = buildMessages(userMessage, context);
            ChatOptions options = ChatOptions.builder()
                    .model(isBlank(model) ? null : model)
                    .maxTokens(config.getMaxTokens())
                    .temperature(config.getTemperature())
                    .build();
            return chatClientRouter.call(messages, options);
        } catch (Exception e) {
            log.error("AI 同步调用失败: {}", e.getMessage(), e);
            return generateFallbackReply(userMessage);
        }
    }

    // ======================== 流式模式 ========================

    @Override
    public Flux<String> chatStream(String userMessage, String context, String model) {
        if (chatClientRouter.current() == null) {
            log.info("AI 通道不可用，使用模拟流式回复");
            return mockStreamReply(userMessage);
        }

        try {
            List<ChatMessage> messages = buildMessages(userMessage, context);
            ChatOptions options = ChatOptions.builder()
                    .model(isBlank(model) ? null : model)
                    .maxTokens(config.getMaxTokens())
                    .temperature(config.getTemperature())
                    .build();

            return chatClientRouter.stream(messages, options)
                    .doOnNext(chunk -> {
                        if (log.isDebugEnabled()) {
                            log.debug("[AI-stream] chunk={}", chunk.length() > 60 ? chunk.substring(0, 60) + "..." : chunk);
                        }
                    })
                    .doOnComplete(() -> log.info("[AI-stream] 完成（channel={}）", chatClientRouter.current().name()))
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
     * 构建消息列表：系统提示词（模板服务动态获取）+ 会话上下文摘要 + 用户消息
     */
    private List<ChatMessage> buildMessages(String userMessage, String context) {
        List<ChatMessage> messages = new ArrayList<>();

        // 系统提示词：优先取 prompt_template 表 chat_system 场景的生效模板，缺失回退内置配置
        String systemPrompt = promptTemplateService.render(
                PromptTemplateService.SCENE_CHAT_SYSTEM, Map.of(), config::getSystemPrompt);
        messages.add(ChatMessage.system(systemPrompt));

        // RAG：检索知识库相关片段作为附加上下文（未启用/未索引/失败时静默跳过）
        String ragContext = ragService.buildContext(userMessage);
        if (ragContext != null && !ragContext.isBlank()) {
            messages.add(ChatMessage.assistant(ragContext));
        }

        // 会话上下文摘要
        if (!isBlank(context)) {
            messages.add(ChatMessage.assistant("以下是之前的对话摘要，请基于此继续对话：\n" + context));
        }

        messages.add(ChatMessage.user(userMessage));
        return messages;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ======================== 降级 / 模拟回复 ========================

    /**
     * 本地模拟回复（当 AI 通道不可用或调用失败时降级使用）
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
