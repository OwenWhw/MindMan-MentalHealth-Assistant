package com.mindman.common.exception;

/**
 * 资源不存在异常 (404)
 */
public class NotFoundException extends BizException {
    public NotFoundException(String message) {
        super(404, message);
    }
}