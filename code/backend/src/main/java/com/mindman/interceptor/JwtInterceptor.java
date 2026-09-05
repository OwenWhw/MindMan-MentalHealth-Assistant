package com.mindman.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindman.common.R;
import com.mindman.common.ResultCode;
import com.mindman.common.enums.StatusEnum;
import com.mindman.entity.User;
import com.mindman.mapper.UserMapper;
import com.mindman.util.JwtUtil;
import com.mindman.util.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * JWT 拦截器 - 完整认证流程。
 *
 * <h3>处理流程</h3>
 * <ol>
 *   <li>放行 OPTIONS 请求（CORS 预检）</li>
 *   <li>校验 Authorization: Bearer &lt;token&gt; 头</li>
 *   <li>解析 Token，提取 userId / username</li>
 *   <li>查询数据库验证用户状态（是否存在、是否禁用）</li>
 *   <li>将用户信息写入 ThreadLocal（LoginUser）</li>
 *   <li>设置 Spring Security 认证上下文（支持 @PreAuthorize 等权限注解）</li>
 * </ol>
 *
 * <h3>异常响应格式</h3>
 * <p>所有异常均使用 {@link R} 统一格式返回，与全局异常处理保持一致。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        // ── 1. CORS 预检直接放行 ──
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        // ── 2. 提取并校验 Bearer Token ──
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeError(response, R.error(ResultCode.UNAUTHORIZED.getCode(), "缺少有效的 Authorization 头"));
            return false;
        }

        String token = header.substring(7).trim();
        if (token.isEmpty()) {
            writeError(response, R.error(ResultCode.UNAUTHORIZED.getCode(), "Token 不能为空"));
            return false;
        }

        // ── 3. 验证 Token 并获取用户信息 ──
        Long userId;
        String username;
        try {
            // 3a. 检查 Token 是否过期
            if (!jwtUtil.isValid(token)) {
                log.warn("Token 已过期");
                writeError(response, R.tokenExpired());
                return false;
            }
            // 3b. 解析 userId 和 username
            userId = jwtUtil.getUserId(token);
            username = jwtUtil.parseToken(token).get("username", String.class);
        } catch (Exception e) {
            log.warn("JWT 解析失败: {}", e.getMessage());
            writeError(response, R.error(ResultCode.UNAUTHORIZED.getCode(), "Token 无效或已过期"));
            return false;
        }

        // ── 4. 查询数据库验证用户状态 ──
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("用户不存在: userId={}", userId);
            writeError(response, R.error(ResultCode.UNAUTHORIZED.getCode(), "用户不存在或已被删除"));
            return false;
        }
        if (user.getStatus() == null || user.getStatus() == StatusEnum.DISABLED.getCode()) {
            log.warn("账号已被禁用: userId={}, username={}", userId, user.getUsername());
            writeError(response, R.error(com.mindman.common.ResultCode.ACCOUNT_DISABLED));
            return false;
        }

        // ── 5. 写入 ThreadLocal 上下文 ──
        LoginUser.set(userId, username, user.getRole());

        // ── 6. 设置 Spring Security 认证信息（支持 @PreAuthorize 权限校验）──
        List<SimpleGrantedAuthority> authorities =
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + (user.getRole() != null ? user.getRole() : "USER")));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("JWT 认证成功: userId={}, username={}", userId, username);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 清理 ThreadLocal，防止线程复用污染
        LoginUser.clear();
        // 清理 SecurityContext
        SecurityContextHolder.clearContext();
    }

    /**
     * 使用 {@link R} 统一格式写入错误响应。
     * <p>与 GlobalExceptionHandler 的返回格式完全一致，前端可统一解析。</p>
     */
    private void writeError(HttpServletResponse response, R<?> result) throws IOException {
        response.setStatus(result.getCode());
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), result);
    }
}
