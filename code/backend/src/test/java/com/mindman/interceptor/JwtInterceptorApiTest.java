package com.mindman.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindman.common.exception.GlobalExceptionHandler;
import com.mindman.controller.AuthController;
import com.mindman.entity.User;
import com.mindman.mapper.UserMapper;
import com.mindman.service.UserService;
import com.mindman.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class JwtInterceptorApiTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final UserService userService = mock(UserService.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JwtInterceptor interceptor = new JwtInterceptor(jwtUtil, userMapper, new ObjectMapper());
        mockMvc = standaloneSetup(new AuthController(userService))
                .addInterceptors(interceptor)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void protectedEndpointRejectsMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(jwtUtil, userMapper, userService);
    }

    @Test
    void protectedEndpointRejectsInvalidBearerToken() throws Exception {
        when(jwtUtil.isValid("bad-token")).thenReturn(false);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        verify(jwtUtil).isValid("bad-token");
        verifyNoInteractions(userMapper, userService);
    }

    @Test
    void validBearerTokenLoadsCurrentUserAndCallsController() throws Exception {
        Claims claims = mock(Claims.class);
        User user = new User();
        user.setId(42L);
        user.setUsername("test-user");
        user.setNickname("本地测试用户");
        user.setRole("user");
        user.setStatus(1);

        when(jwtUtil.isValid("good-token")).thenReturn(true);
        when(jwtUtil.getUserId("good-token")).thenReturn(42L);
        when(jwtUtil.parseToken("good-token")).thenReturn(claims);
        when(claims.get("username", String.class)).thenReturn("test-user");
        when(userMapper.selectById(42L)).thenReturn(user);
        when(userService.currentUser(42L)).thenReturn(user);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer good-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("test-user"))
                .andExpect(jsonPath("$.data.nickname").value("本地测试用户"));

        verify(userMapper).selectById(42L);
        verify(userService).currentUser(42L);
    }

    @Test
    void disabledAccountCannotUseValidToken() throws Exception {
        Claims claims = mock(Claims.class);
        User user = new User();
        user.setId(42L);
        user.setUsername("test-user");
        user.setRole("user");
        user.setStatus(0);

        when(jwtUtil.isValid("disabled-token")).thenReturn(true);
        when(jwtUtil.getUserId("disabled-token")).thenReturn(42L);
        when(jwtUtil.parseToken("disabled-token")).thenReturn(claims);
        when(claims.get("username", String.class)).thenReturn("test-user");
        when(userMapper.selectById(42L)).thenReturn(user);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer disabled-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        verifyNoInteractions(userService);
    }
}
