package com.mindman.service.impl;

import com.mindman.ai.ChatClientRouter;
import com.mindman.ai.ChatMessage;
import com.mindman.ai.RagService;
import com.mindman.config.AiConfig;
import com.mindman.service.PromptTemplateService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatServiceImplTest {

    private final ChatClientRouter router = mock(ChatClientRouter.class);
    private final PromptTemplateService promptTemplates = mock(PromptTemplateService.class);
    private final RagService ragService = mock(RagService.class);
    private final AiChatServiceImpl service = new AiChatServiceImpl(
            new AiConfig(), router, promptTemplates, ragService);

    @Test
    void currentInteractionRulesAreAppendedAfterAnOlderActiveDatabasePrompt() {
        when(promptTemplates.render(eq(PromptTemplateService.SCENE_CHAT_SYSTEM), anyMap(), any()))
                .thenReturn("旧数据库提示词：每轮都用表情，并在结尾追问。");
        when(ragService.buildContext(anyString())).thenReturn(null);
        when(router.call(anyList(), any())).thenReturn("收到。");

        service.chatSync("今天有点累", "", null);

        ArgumentCaptor<List<ChatMessage>> messages = ArgumentCaptor.forClass(List.class);
        verify(router).call(messages.capture(), any());
        String effectivePrompt = messages.getValue().get(0).content();
        assertTrue(effectivePrompt.startsWith("旧数据库提示词"));
        assertTrue(effectivePrompt.contains("【对话互动规则｜优先于上方旧模板】"));
        assertTrue(effectivePrompt.contains("不为每轮都补一个问题"));
        assertTrue(effectivePrompt.contains("没有检索到的文章标题"));
        assertTrue(effectivePrompt.contains("每段只讲一个意思"));
        assertTrue(effectivePrompt.contains("不要在句子中间输出 ###"));
        assertTrue(effectivePrompt.contains("避免英语语序直译"));
    }

    @Test
    void articleTranslationUsesTheSelectedTextWithoutAddingUnrelatedRagSnippets() {
        when(promptTemplates.render(eq(PromptTemplateService.SCENE_CHAT_SYSTEM), anyMap(), any()))
                .thenReturn("基础提示词");
        when(router.call(anyList(), any())).thenReturn("完成");

        service.chatSync("请翻译并解读这篇文章", "【文章翻译任务标记】\\n可用正文：Post-Parton depression", null);

        ArgumentCaptor<List<ChatMessage>> messages = ArgumentCaptor.forClass(List.class);
        verify(router).call(messages.capture(), any());
        assertTrue(messages.getValue().get(0).content().contains("【文章翻译专用规则】"));
        assertTrue(messages.getValue().stream().noneMatch(message -> message.content().contains("RAG 测试片段")));
        verify(ragService, never()).buildContext(anyString());
    }
}
