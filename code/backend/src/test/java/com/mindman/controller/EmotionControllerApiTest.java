package com.mindman.controller;

import com.mindman.ai.MindManAgent;
import com.mindman.common.exception.GlobalExceptionHandler;
import com.mindman.dto.EmotionGardenVO;
import com.mindman.dto.EmotionRecordSaveDTO;
import com.mindman.dto.GardenInsightVO;
import com.mindman.service.EmotionRecordService;
import com.mindman.util.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class EmotionControllerApiTest {

    private final EmotionRecordService emotionRecordService = mock(EmotionRecordService.class);
    private final MindManAgent mindManAgent = mock(MindManAgent.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = standaloneSetup(new EmotionController(emotionRecordService, mindManAgent))
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
    void getGardenUsesCurrentUserAndReturnsFlowerList() throws Exception {
        EmotionGardenVO flower = new EmotionGardenVO();
        flower.setFlowerId(101L);
        flower.setEmotion("焦虑");
        flower.setContent("测试记录");
        flower.setEmotionScore(3);
        flower.setRatingSource("self_reported");
        when(emotionRecordService.listGarden(42L)).thenReturn(List.of(flower));

        mockMvc.perform(get("/api/emotion/garden"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].flowerId").value(101))
                .andExpect(jsonPath("$.data[0].emotion").value("焦虑"))
                .andExpect(jsonPath("$.data[0].content").value("测试记录"))
                .andExpect(jsonPath("$.data[0].ratingSource").value("self_reported"));

        verify(emotionRecordService).listGarden(42L);
    }

    @Test
    void gardenInsightValidatesCurrentNoteAndReturnsAgentResult() throws Exception {
        GardenInsightVO insight = new GardenInsightVO();
        insight.setSource("agent");
        insight.setEvidence("今天开会时");
        insight.setObservation("开会是这条记录里的具体场景。");
        insight.setQuestion("当时最让你在意的是什么？");
        when(mindManAgent.analyzeGardenDraft(any())).thenReturn(insight);

        mockMvc.perform(post("/api/emotion/garden/insight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emotion\":\"焦虑\",\"content\":\"今天开会时有点紧张\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("agent"))
                .andExpect(jsonPath("$.data.evidence").value("今天开会时"));
        verify(mindManAgent).analyzeGardenDraft(any());
    }

    @Test
    void plantValidatesAndPassesCurrentUserAndFormValues() throws Exception {
        EmotionGardenVO flower = new EmotionGardenVO();
        flower.setFlowerId(102L);
        flower.setEmotion("平静");
        when(emotionRecordService.plant(eq(42L), any(EmotionRecordSaveDTO.class))).thenReturn(flower);

        mockMvc.perform(post("/api/emotion/garden")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emotion\":\"平静\",\"content\":\"今天完成了测试\",\"emotionScore\":4,\"sleepScore\":3,\"stressScore\":2,\"trigger\":\"工作\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.flowerId").value(102))
                .andExpect(jsonPath("$.data.emotion").value("平静"));

        verify(emotionRecordService).plant(eq(42L), any(EmotionRecordSaveDTO.class));
    }

    @Test
    void plantRejectsMissingEmotionBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/emotion/garden")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"没有选择情绪\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("请选择一种心情")));

        verifyNoInteractions(emotionRecordService);
    }

    @Test
    void plantRejectsMissingRatingsInsteadOfSavingDatabaseDefaults() throws Exception {
        mockMvc.perform(post("/api/emotion/garden")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emotion\":\"平静\",\"content\":\"完成记录\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("请按自己的感受选择")));

        verifyNoInteractions(emotionRecordService);
    }

    @Test
    void updateAndDeleteForwardFlowerIdAndCurrentUser() throws Exception {
        EmotionGardenVO flower = new EmotionGardenVO();
        flower.setFlowerId(103L);
        flower.setEmotion("开心");
        when(emotionRecordService.update(eq(42L), eq(103L), any(EmotionRecordSaveDTO.class))).thenReturn(flower);

        mockMvc.perform(put("/api/emotion/garden/103")
                .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emotion\":\"开心\",\"emotionScore\":5,\"sleepScore\":4,\"stressScore\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flowerId").value(103));

        mockMvc.perform(delete("/api/emotion/garden/103"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(emotionRecordService).update(eq(42L), eq(103L), any(EmotionRecordSaveDTO.class));
        verify(emotionRecordService).deleteOwn(42L, 103L);
    }

    @Test
    void nonAdminCannotQueryAdminEmotionDiary() throws Exception {
        mockMvc.perform(get("/api/emotion/diary/page"))
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(emotionRecordService);
    }
}
