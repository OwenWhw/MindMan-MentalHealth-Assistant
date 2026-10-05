package com.mindman.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.mindman.ai.MindManAgent;
import com.mindman.dto.ChatSendDTO;
import com.mindman.entity.ChatMessage;
import com.mindman.entity.ChatSession;
import com.mindman.mapper.ChatMessageMapper;
import com.mindman.mapper.ChatSessionMapper;
import com.mindman.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceImplTest {

    private final ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
    private final ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final MindManAgent mindManAgent = mock(MindManAgent.class);
    private final ChatServiceImpl service = new ChatServiceImpl(
            sessionMapper, messageMapper, userMapper, mindManAgent);

    @Test
    void restoreAndSummaryUpdatesAreScopedToSessionOwner() {
        ChatSession session = session(501L, 42L);
        session.setStatus(2);
        when(sessionMapper.selectById(501L)).thenReturn(session);

        service.restoreSession(42L, 501L);
        service.saveSessionSummary(42L, 501L, "聊到了工作变化和焦虑感受。");

        ArgumentCaptor<UpdateWrapper<ChatSession>> updates = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(sessionMapper, times(2)).update(isNull(), updates.capture());
        assertTrue(updates.getAllValues().stream().allMatch(update -> update.getSqlSegment().contains("user_id")));
        assertTrue(updates.getAllValues().get(0).getParamNameValuePairs().containsValue(1));
        assertTrue(updates.getAllValues().get(1).getParamNameValuePairs().containsValue("聊到了工作变化和焦虑感受。"));
    }

    @Test
    void failedAiCallKeepsUserMessageAndPersistsFailedAssistantState() {
        ChatSession session = session(501L, 42L);
        when(sessionMapper.selectById(501L)).thenReturn(session);
        when(messageMapper.insert(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(message.getRole().equals("user") ? 701L : 702L);
            return 1;
        });
        when(mindManAgent.respondSync(any(), any(), any())).thenThrow(new IllegalStateException("AI 暂时不可用"));

        com.mindman.dto.ChatSendDTO request = new com.mindman.dto.ChatSendDTO();
        request.setSessionId(501L);
        request.setContent("今天有点焦虑");

        assertThrows(IllegalStateException.class, () -> service.sendMessage(42L, request));

        verify(messageMapper, times(2)).insert(any(ChatMessage.class));
        verify(messageMapper).updateById(argThat((ChatMessage message) ->
                "assistant".equals(message.getRole())
                        && "failed".equals(message.getDeliveryStatus())
                        && message.getContent().isEmpty()));
        verify(sessionMapper).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void quickActionStoresItsDisplayLabelButSendsTheFullInstructionToTheAgent() {
        ChatSession session = session(501L, 42L);
        when(sessionMapper.selectById(501L)).thenReturn(session);
        when(messageMapper.insert(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(message.getRole().equals("user") ? 701L : 702L);
            return 1;
        });
        when(mindManAgent.respondSync(eq(42L), any(ChatSendDTO.class), anyString())).thenReturn("回顾完成");

        ChatSendDTO request = new ChatSendDTO();
        request.setSessionId(501L);
        request.setContent("请结合我近30天的情绪花园记录，温和回顾出现较多的情绪和评分。请区分事实与推测。");
        request.setDisplayContent("回顾近30天的情绪花园");

        service.sendMessage(42L, request);

        ArgumentCaptor<ChatMessage> inserted = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageMapper, times(2)).insert(inserted.capture());
        assertEquals("回顾近30天的情绪花园", inserted.getAllValues().get(0).getContent());
        verify(mindManAgent).respondSync(eq(42L), same(request), anyString());
    }

    private ChatSession session(Long id, Long userId) {
        ChatSession session = new ChatSession();
        session.setId(id);
        session.setUserId(userId);
        session.setTitle("新的咨询");
        session.setStatus(1);
        return session;
    }
}
