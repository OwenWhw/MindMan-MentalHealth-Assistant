package com.mindman.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录响应 VO
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li>{@code userId}    - 用户唯一 ID，前端存到 authStore</li>
 *   <li>{@code username}  - 登录名（不可修改）</li>
 *   <li>{@code nickname}  - 昵称，首页"早上好，{nickname}" 使用</li>
 *   <li>{@code avatar}    - 头像 URL（无则用字母 avatar 兜底渲染）</li>
 *   <li>{@code role}      - admin/user，前端根据 role 决定是否显示管理菜单</li>
 *   <li>{@code token}     - JWT，存到 localStorage，后续请求 Header 携带</li>
 *   <li>{@code expiresAt} - Token 过期时间，前端可倒计时提前提示</li>
 * </ul>
 *
 * <h3>前端用法</h3>
 * <pre>
 * const { data } = await login({username, password})
 * authStore.setUserInfo({
 *   id: data.userId, nickname: data.nickname,
 *   avatar: data.avatar, role: data.role
 * })
 * localStorage.setItem('token', data.token)
 * </pre>
 */
@Data
@Builder
public class LoginVO {

    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    private String role;
    private String token;
    private LocalDateTime expiresAt;
}