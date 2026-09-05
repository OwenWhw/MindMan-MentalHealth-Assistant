package com.mindman.util;

import com.mindman.common.enums.RoleEnum;
import com.mindman.common.exception.ForbiddenException;

/**
 * 当前登录用户上下文（基于 ThreadLocal）
 *
 * <pre>
 * // Controller / Service 任意处调用
 * Long uid = LoginUser.get();
 * String role = LoginUser.role();
 * LoginUser.requireAdmin();   // 管理端接口鉴权，非管理员抛出 403
 * </pre>
 */
public class LoginUser {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ROLE = new ThreadLocal<>();

    public static void set(Long userId, String username, String role) {
        USER_ID.set(userId);
        USERNAME.set(username);
        USER_ROLE.set(role);
    }

    public static Long get() {
        return USER_ID.get();
    }

    public static String username() {
        return USERNAME.get();
    }

    public static String role() {
        return USER_ROLE.get();
    }

    /**
     * 管理端接口鉴权：当前用户非管理员时抛出 403 Forbidden。
     */
    public static void requireAdmin() {
        if (!RoleEnum.ADMIN.getCode().equals(USER_ROLE.get())) {
            throw new ForbiddenException("无权限访问该资源");
        }
    }

    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
        USER_ROLE.remove();
    }
}