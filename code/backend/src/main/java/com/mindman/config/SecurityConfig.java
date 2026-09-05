package com.mindman.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置。
 *
 * <h3>架构说明</h3>
 * <p>本项目采用「Spring Security 轻量 + JWT 拦截器认证」的混合方案：</p>
 * <ul>
 *   <li>Spring Security 负责：密码编码器(BCrypt)、禁用 CSRF、无状态会话策略</li>
 *   <li>{@link com.mindman.interceptor.JwtInterceptor} 负责：Token 校验、用户状态查询、
 *       ThreadLocal 上下文写入、SecurityContext 认证信息设置</li>
 * </ul>
 *
 * <h3>为什么 permitAll()？</h3>
 * <p>Spring Security 过滤器链运行在 DispatcherServlet <strong>之前</strong>，
 * 如果配置 {@code .anyRequest().authenticated()}，请求在到达 JwtInterceptor 前就会被
 * Security 以「未认证」拒绝（返回 401），导致拦截器的 Token 校验逻辑永远不会执行。</p>
 *
 * <p>因此这里将所有路径设为 {@code permitAll()}，实际的身份认证和权限控制完全由
 * JwtInterceptor 在 WebMvc 层完成。JwtInterceptor 验证通过后会主动设置
 * {@code SecurityContextHolder}，使 {@code @PreAuthorize} 等注解仍可正常工作。</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * BCrypt 密码编码器（用于注册加密 + 登录比对）
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（JWT 无状态，不需要 CSRF 保护）
            .csrf(AbstractHttpConfigurer::disable)
            // 无状态会话（不使用 Session 存储认证信息）
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 所有请求放行 —— 实际认证由 JwtInterceptor 在 WebMvc 层完成
            // （原因：Security 过滤器链在 DispatcherServlet 之前执行，
            //   若配置 authenticated() 会拦截掉所有未携带 Security 认证的请求）
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );
        return http.build();
    }
}
