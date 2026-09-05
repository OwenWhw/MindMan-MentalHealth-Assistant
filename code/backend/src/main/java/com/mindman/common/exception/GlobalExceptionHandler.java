package com.mindman.common.exception;

import com.mindman.common.R;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * 全局异常处理 - 统一拦截所有异常并返回 R 格式。
 *
 * <pre>
 * 处理顺序：
 *  BizException          → 业务异常（优先匹配）
 *  MethodArgumentNotValid  → @RequestBody @Valid 校验失败
 *  BindException          → @ModelAttribute / 普通表单校验失败
 *  ConstraintViolation    → Controller 方法参数校验失败（@Validated on class）
 *  参数类型/缺失异常       → 400
 *  未知 Exception          → 500
 * </pre>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ═══════════ 业务异常 ═══════════

    @ExceptionHandler(BizException.class)
    public R<?> handleBiz(BizException e) {
        log.warn("业务异常 [{}]: {}", e.getCode(), e.getMessage());
        return R.error(e.getCode(), e.getMessage());
    }

    // ═══════════ 参数校验异常 ═══════════

    /**
     * @RequestBody + @Valid 校验失败
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return R.badRequest(msg);
    }

    /**
     * 表单 / @ModelAttribute 校验失败
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleBind(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("表单校验失败: {}", msg);
        return R.badRequest(msg);
    }

    /**
     * Controller 上 @Validated 校验方法参数失败
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数约束违反: {}", msg);
        return R.badRequest(msg);
    }

    // ═══════════ 请求参数格式异常 ═══════════

    /** 缺少必需参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少参数: {}", e.getParameterName());
        return R.badRequest("缺少必需参数: " + e.getParameterName());
    }

    /** 参数类型不匹配 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型错误: {} 期望 {}", e.getName(),
                e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "?");
        return R.badRequest("参数 " + e.getName() + " 类型错误");
    }

    /** 请求体 JSON 格式错误 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return R.badRequest("请求体格式有误，请检查 JSON 格式");
    }

    /**
     * 数据库唯一约束冲突（兜底软删除等预检查漏掉的情况）
     * 典型：用户表 username 唯一，被软删除后再注册同名用户，
     *     selectCount(deleted=0) 看不到，但 INSERT 仍会撞唯一键
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleDuplicateKey(DuplicateKeyException e) {
        String msg = e.getMostSpecificCause() != null
                ? e.getMostSpecificCause().getMessage()
                : e.getMessage();
        log.warn("数据重复: {}", msg);
        // 简单按关键字判断具体字段
        if (msg != null && msg.contains("uk_username")) {
            return R.badRequest("用户名已存在（包括已注销用户）");
        }
        if (msg != null && msg.contains("uk_email")) {
            return R.badRequest("邮箱已被注册");
        }
        return R.badRequest("数据已存在，请检查后重试");
    }

    // ═══════════ 文件上传 ═══════════

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return R.badRequest("上传文件超过大小限制");
    }

    // ═══════════ 兜底 ═══════════

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<?> handle(Exception e) {
        log.error("系统异常", e);
        return R.internalError();
    }
}