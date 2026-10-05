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
 *   <li>通道不可用或调用失败时返回明确错误，不伪造 AI 回复</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    /** Enforced on every chat even when an older/custom database template is active. */
    private static final String CHAT_INTERACTION_GUIDANCE = """
            \n\n【对话互动规则｜优先于上方旧模板】
            先直接完成用户这轮明确提出的事。用户问操作、资料或建议时，先给可核对的结果，不用泛泛安慰替代答案。
            先回应用户刚说的一个具体细节；不复述整段，不用“我听到了你的分享”“谢谢你愿意告诉我”“这一定不容易”“我一直在这里陪着你”等固定套话开头或收尾。
            不为每轮都补一个问题。只有缺少的信息会改变回答时，才问一个简短、开放的问题；用户不必继续透露隐私。
            用户没有明确要建议时，先倾听，不主动塞解决办法；只有确有具体建议可供选择时，才简短询问是否想听。明确要建议时，给 1–2 个小而具体、今天可尝试的动作，并说明怎么开始。
            不默认加 emoji、感叹号、鼓励口号或反问句。通常用 2–5 句；复杂的实用问题可以按需展开。
            中文回答必须清楚分段：先直接回应核心问题，再按逻辑补充依据或做法；每段只讲一个意思，通常 1–3 句，段落之间空一行。超过两个并列要点时使用简短列表。
            需要小标题时，标题单独一行，使用规范 Markdown（例如“### 文章要点”），标题和正文之间空一行；禁止把标题、正文和多个观点连成一个长段，也不要在句子中间输出 ###、** 等格式符号。
            表达要像自然中文：句子主谓清楚，避免英语语序直译、残缺短语和生硬套话；忠实原意的同时改写成通顺易懂的中文。原文含义不明时说明不确定处，不要猜着补全。
            只把已提供的会话、用户明确选择的花园记录和站内资料当作依据。没有检索到的文章标题、作者、来源、链接、数字或用户经历，一律不编造。
            花园分析先列记录事实，再谨慎表达可能的联系；样本不足要明说，不作诊断，也不把应用评分描述为临床评估。
            翻译文章时忠实呈现已提供的文字。遇到双关、文字游戏或专名，保留原文短语并简要解释；不要把修辞误写成心理或医学术语。
            """;

    private static final String ARTICLE_TRANSLATION_GUIDANCE = """
            \n\n【文章翻译专用规则】
            只翻译用户选中的文章在上下文中列出的可用正文；禁止依标题扩写或把摘要当全文。使用自然准确的简体中文，不照搬英语语序，不写残缺句；严格区分原文、摘要和你的解释。保留有意义的双关原词并简短解释，不能将它误认成临床术语。涉及人物心理时只转述文章明确内容，不自行推断；涉及近期事件时表述为“文章称”，不声称独立核实。
            回复结构：每个标题和正文各占独立段落；依次为“译文”（按原文顺序，忠实翻译）、“文章要点”（最多 3 点）、“谨慎解读”（最多 2 句，区分文章所写与谨慎理解）。译文保留原文段落；要点使用简短列表。禁止将标题和正文写在同一行，不加通用安慰、无根据的心理分析或结尾反问。若可用材料是摘要/片段，简短标明翻译范围。
            """;

    private final AiConfig config;
    private final ChatClientRouter chatClientRouter;
    private final PromptTemplateService promptTemplateService;
    private final RagService ragService;

    // ======================== 同步模式 ========================

    @Override
    public String chatSync(String userMessage, String context, String model) {
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
            throw new IllegalStateException("AI 服务暂时不可用，请稍后重试", e);
        }
    }

    // ======================== 流式模式 ========================

    @Override
    public Flux<String> chatStream(String userMessage, String context, String model) {
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
                    .doOnComplete(() -> log.info("[AI-stream] 完成"))
                    .doOnError(e -> log.error("AI 流式调用失败: {}", e.getMessage()));
        } catch (Exception e) {
            log.error("AI 流式调用初始化失败: {}", e.getMessage());
            return Flux.error(new IllegalStateException("AI 服务暂时不可用，请稍后重试", e));
        }
    }

    @Override
    public String summarizeConversation(String transcript) {
        if (isBlank(transcript)) {
            throw new IllegalArgumentException("没有可总结的对话内容");
        }
        List<ChatMessage> messages = List.of(
                ChatMessage.system("""
                        你是 MindMan 的温和心理健康倾听助手。请根据用户明确分享的对话，写一份简洁、易读的回顾。
                        分为「聊到的事情」「出现的感受」「可以继续留意」三部分；区分用户明确说过的事实与谨慎推测，不做诊断，不夸大结论，不提出过多建议。若内容不足，说明信息有限。使用简体中文，语气自然克制。
                        """),
                ChatMessage.user("请总结以下本次对话记录：\n\n" + transcript)
        );
        try {
            return chatClientRouter.call(messages, ChatOptions.builder()
                    .maxTokens(900)
                    .temperature(0.35)
                    .build());
        } catch (Exception e) {
            log.error("AI 对话总结失败: {}", e.getMessage(), e);
            throw new IllegalStateException("AI 总结暂时不可用，请稍后重试", e);
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
        boolean articleTranslationTask = context != null && context.contains("【文章翻译任务标记】");
        messages.add(ChatMessage.system(systemPrompt + CHAT_INTERACTION_GUIDANCE
                + (articleTranslationTask ? ARTICLE_TRANSLATION_GUIDANCE : "")));

        // Strict translation must use only the selected article text; unrelated RAG excerpts can contaminate it.
        if (!articleTranslationTask) {
            String ragContext = ragService.buildContext(userMessage);
            if (ragContext != null && !ragContext.isBlank()) {
                messages.add(ChatMessage.assistant(ragContext));
            }
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
}
