package com.mindman.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LocalUserEmotionWorkflowTest {

    private static final String PASSWORD = "LocalTest123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerLoginAndEmotionGardenWorkflowIsIsolatedPerUser() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String phoneSuffix = String.format("%08d", Math.abs(System.nanoTime() % 100_000_000));
        String usernameA = "flowa" + suffix;
        String usernameB = "flowb" + suffix;

        mockMvc.perform(get("/api/emotion/garden"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        String tokenA = register(usernameA, "138" + phoneSuffix);
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(usernameA))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.deleted").doesNotExist());

        mockMvc.perform(get("/api/emotion/diary/page").header("Authorization", bearer(tokenA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        String tokenB = register(usernameB, "139" + phoneSuffix);
        String loginTokenA = login(usernameA);
        assertThat(loginTokenA).isNotBlank();

        MvcResult created = mockMvc.perform(post("/api/emotion/garden")
                        .header("Authorization", bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emotion":"平静","content":"本地流程测试","emotionScore":4,"sleepScore":3,"stressScore":2,"trigger":"测试"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emotion").value("平静"))
                .andExpect(jsonPath("$.data.content").value("本地流程测试"))
                .andExpect(jsonPath("$.data.ratingSource").value("self_reported"))
                .andReturn();

        long flowerId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("flowerId").asLong();
        assertThat(flowerId).isPositive();

        mockMvc.perform(get("/api/emotion/garden").header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].flowerId").value(flowerId));

        mockMvc.perform(get("/api/emotion/garden").header("Authorization", bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(put("/api/emotion/garden/{id}", flowerId)
                        .header("Authorization", bearer(tokenB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emotion\":\"开心\",\"content\":\"不应越权修改\",\"emotionScore\":4,\"sleepScore\":4,\"stressScore\":4}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/emotion/garden/{id}", flowerId)
                        .header("Authorization", bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emotion\":\"开心\",\"content\":\"已更新\",\"emotionScore\":5,\"sleepScore\":4,\"stressScore\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emotion").value("开心"))
                .andExpect(jsonPath("$.data.content").value("已更新"))
                .andExpect(jsonPath("$.data.ratingSource").value("self_reported"));

        mockMvc.perform(delete("/api/emotion/garden/{id}", flowerId)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/emotion/garden").header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private String register(String username, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Registration(username, PASSWORD, username, phone))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = body.path("data").path("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Login(username, PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private record Registration(String username, String password, String nickname, String phone) { }

    private record Login(String username, String password) { }
}
