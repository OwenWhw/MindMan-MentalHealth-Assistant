package com.mindman.common;

/**
 * 统一 API 响应状态码枚举
 */
public enum ResultCode {

    // 2xx 成功
    SUCCESS(200, "操作成功"),

    // 4xx 客户端错误
    BAD_REQUEST(400, "请求参数有误"),
    UNAUTHORIZED(401, "请先登录"),
    TOKEN_EXPIRED(40101, "登录已过期，请重新登录"),
    FORBIDDEN(403, "没有权限访问"),
    ACCOUNT_DISABLED(40301, "账号已被禁用"),
    NOT_FOUND(404, "资源不存在"),

    // 5xx 服务端错误
    INTERNAL_ERROR(500, "服务器内部错误"),
    SERVICE_UNAVAILABLE(503, "服务暂不可用");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}