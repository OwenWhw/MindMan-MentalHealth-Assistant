package com.mindman.common.exception;

/**
 * 业务异常基类（提供常用 code 快捷构造）
 */
public abstract class BizException extends RuntimeException {

    private final int code;

    protected BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    protected BizException(String message) {
        this(500, message);
    }

    public int getCode() {
        return code;
    }
}