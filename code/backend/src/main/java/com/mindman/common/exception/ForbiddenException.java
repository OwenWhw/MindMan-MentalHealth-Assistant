package com.mindman.common.exception;

/**
 * 无权限访问 (403)
 */
public class ForbiddenException extends BizException {
    public ForbiddenException(String message) {
        super(403, message);
    }
    public ForbiddenException() {
        super(403, "没有权限访问");
    }
}