package com.mindman.common.exception;

/**
 * 未登录 / Token 失效 (401)
 */
public class UnauthorizedException extends BizException {
    public UnauthorizedException() {
        super(401, "请先登录");
    }
    public UnauthorizedException(String message) {
        super(401, message);
    }
}