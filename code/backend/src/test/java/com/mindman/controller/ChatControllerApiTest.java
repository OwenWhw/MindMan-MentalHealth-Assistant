package com.mindman.controller;

import com.mindman.common.exception.GlobalExceptionHandler;
import com.mindman.ai.MindManAgent;
import com.mindman.dto.ChatMessageVO;
import com.mindman.dto.ChatSessionCreateDTO;
import com.mindman.dto.ChatSessionVO;
import com.mindman.dto.ChatSendDTO;
import com.mindman.mapper.ChatMessageMapper;
import com.mindman.service.ChatService;
import com.mindman.util.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.mockito.ArgumentCaptor;

class ChatControllerApiTest {

    private final ChatService chatService = mock(ChatService.class);
    private final MindManAgent mindManAgent = mock(MindManAgent.class);
    private final ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = standaloneSetup(new ChatController(chatService, messageMapper, mindManAgent))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
        LoginUser.set(42L, "test-user", "user");
    }

    @AfterEach
    void clearLoginContext() {
        LoginUser.clear();
    }

    @Test
    void createSessionUsesCurrentUserAndReturnsSession() throws Exception {
        ChatSessionVO session = ChatSessionVO.builder()
                .id(501L)
                .title("今天的心情")
                .status(1)
                .statusText("进行中")
                .build();
        when(chatService.createSession(eq(42L), any(ChatSessionCreateDTO.class))).thenReturn(session);

        mockMvc.perform(post("/api/chat/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"今天的心情\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(501))
                .andExpect(jsonPath("$.data.title").value("今天的心情"));

        verify(chatService).createSession(eq(42L), any(ChatSessionCreateDTO.class));
    }

    @Test
    void sendMessageReturnsUserAndAssistantMessages() throws Exception {
        List<ChatMessageVO> messages = List.of(
                ChatMessageVO.builder().id(701L).sessionId(501L).role("user").content("我有点焦虑").build(),
                ChatMessageVO.builder().id(702L).sessionId(501L).role("assistant").content("听起来这段时间不容易。").emotion("焦虑").build()
        );
        when(chatService.sendMessage(eq(42L), any(ChatSendDTO.class))).thenReturn(messages);

        mockMvc.perform(post("/api/chat/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":501,\"content\":\"我有点焦虑\",\"model\":\"test-model\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].role").value("user"))
                .andExpect(jsonPath("$.data[1].role").value("assistant"))
                .andExpect(jsonPath("$.data[1].emotion").value("焦虑"));

        verify(chatService).sendMessage(eq(42L), any(ChatSendDTO.class));
    }

    @Test
    void messageHistoryUsesDefaultPageAndSize() throws Exception {
        when(chatService.listMessages(42L, 501L, 1, 20)).thenReturn(List.of(
                ChatMessageVO.builder().id(701L).sessionId(501L).role("user").content("历史消息").build()
        ));

        mockMvc.perform(get("/api/chat/sessions/501/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].content").value("历史消息"));

        verify(chatService).listMessages(42L, 501L, 1, 20);
    }

    @Test
    void sendMessageRejectsMissingSessionAndContentBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/chat/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"缺少会话 ID\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());

        verifyNoInteractions(chatService, messageMapper, mindManAgent);
    }

    @Test
    void streamingMessageDelegatesToInternalAgent() throws Exception {
        when(chatService.getSessionEntity(42L, 501L)).thenReturn(new com.mindman.entity.ChatSession());
        when(messageMapper.selectList(any())).thenReturn(new ArrayList<>());

        when(mindManAgent.respond(eq(42L), any(ChatSendDTO.class), anyString()))
                .thenReturn(Flux.just("我看到这段时间的变化了。"));

        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":501,\"content\":\"帮我看看近况\",\"model\":\"test-model\","
                                + "\"includeGardenContext\":true,\"referenceArticleId\":77}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        verify(mindManAgent, timeout(3000)).respond(eq(42L), any(ChatSendDTO.class), anyString());
    }

    @Test
    void archivedSessionCanBeRestoredOnlyThroughOwnedService() throws Exception {
        mockMvc.perform(put("/api/chat/sessions/501/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(chatService).restoreSession(42L, 501L);
    }

    @Test
    void summaryUsesOnlyOwnedSessionMessagesAndReturnsAiSummary() throws Exception {
        when(chatService.getSessionEntity(42L, 501L)).thenReturn(new com.mindman.entity.ChatSession());
        com.mindman.entity.ChatMessage user = new com.mindman.entity.ChatMessage();
        user.setId(701L);
        user.setSessionId(501L);
        user.setUserId(42L);
        user.setRole("user");
        user.setContent("最近工作安排变化，让我有些焦虑");
        com.mindman.entity.ChatMessage assistant = new com.mindman.entity.ChatMessage();
        assistant.setId(702L);
        assistant.setSessionId(501L);
        assistant.setUserId(42L);
        assistant.setRole("assistant");
        assistant.setContent("变化发生时感到不安是可以理解的。");
        when(messageMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(assistant, user)));
        when(mindManAgent.summarize(anyString())).thenReturn("聊到的事情：工作变化。\n出现的感受：焦虑。\n可以继续留意：哪些部分最让你不安？");

        mockMvc.perform(post("/api/chat/sessions/501/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsString("工作变化")));

        ArgumentCaptor<String> transcript = ArgumentCaptor.forClass(String.class);
        verify(chatService).getSessionEntity(42L, 501L);
        verify(mindManAgent).summarize(transcript.capture());
        verify(chatService).saveSessionSummary(eq(42L), eq(501L), anyString());
        assertTrue(transcript.getValue().contains("最近工作安排变化"));
        assertTrue(transcript.getValue().contains("变化发生时感到不安"));
    }

    @Test
    void summaryRequiresAtLeastOneUserMessage() throws Exception {
        when(chatService.getSessionEntity(42L, 501L)).thenReturn(new com.mindman.entity.ChatSession());
        com.mindman.entity.ChatMessage assistant = new com.mindman.entity.ChatMessage();
        assistant.setRole("assistant");
        assistant.setContent("欢迎来到倾听空间");
        when(messageMapper.selectList(any())).thenReturn(List.of(assistant));

        mockMvc.perform(post("/api/chat/sessions/501/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("当前会话还没有可总结的倾诉内容"));

        verifyNoInteractions(mindManAgent);
    }

    @Test
    void summaryReturnsSavedVersionWithoutCallingAiAgain() throws Exception {
        com.mindman.entity.ChatSession session = new com.mindman.entity.ChatSession();
        session.setSummary("已保存的总结");
        when(chatService.getSessionEntity(42L, 501L)).thenReturn(session);

        mockMvc.perform(post("/api/chat/sessions/501/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("已保存的总结"));

        verifyNoInteractions(mindManAgent, messageMapper);
    }
}
