package com.mindman.common.exception;

/**
 * 通用参数错误 (400)
 */
public class BadRequestException extends BizException {
    public BadRequestException(String message) {
        super(400, message);
    }
}