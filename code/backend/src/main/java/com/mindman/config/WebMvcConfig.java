package com.mindman.config;

import com.mindman.interceptor.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册 JWT 拦截器。
 *
 * <p>白名单（不拦截）：/api/auth/** 和 Swagger/Knife4j 文档</p>
 * <p>其它路径必须带 Authorization Bearer token</p>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 仅免登录的 auth 接口；me/profile/password 需要登录态（LoginUser）
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/logout",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/doc.html",
                        "/webjars/**",
                        "/error"
                );
    }
}