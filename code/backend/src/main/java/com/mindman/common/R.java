package com.mindman.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mindman.common.page.PageVO;
import lombok.Data;
import lombok.Getter;

import java.io.Serializable;
import java.util.List;

/**
 * 统一 API 响应包装类。
 *
 * <h3>用法示例</h3>
 * <pre>
 * // 成功返回数据
 * return R.ok(data);
 * return R.ok();                           // 无数据
 * return R.created();                      // 201 创建成功
 *
 * // 分页
 * return R.page(pageVO);
 *
 * // 失败
 * return R.error(ResultCode.UNAUTHORIZED);
 * return R.error("用户名已存在");
 * return R.error(400, "参数格式有误");
 *
 * // 链式设置消息（不推荐在数据类场景使用）
 * return R.ok().message("注册成功");
 * </pre>
 *
 * @param <T> 返回数据类型
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> implements Serializable {

    /** 状态码 */
    private int code;

    /** 提示消息 */
    private String message;

    /** 响应数据 */
    private T data;

    /** 链路追踪 ID，对应日志 traceId */
    private String traceId;

    // ──────────────── 工厂方法 ────────────────

    /** 成功（有数据） */
    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = ResultCode.SUCCESS.getCode();
        r.message = ResultCode.SUCCESS.getMessage();
        r.data = data;
        return r;
    }

    /** 成功（无数据） */
    public static <T> R<T> ok() {
        return ok(null);
    }

    /** 资源创建成功（语义标记，code 仍为 200） */
    public static <T> R<T> created(T data) {
        return ok(data);
    }

    /** 分页成功 */
    public static <T> R<PageVO<T>> page(PageVO<T> pageVO) {
        return ok(pageVO);
    }

    // ──────────────── 错误工厂 ────────────────

    /** 使用枚举错误码 */
    public static <T> R<T> error(ResultCode resultCode) {
        R<T> r = new R<>();
        r.code = resultCode.getCode();
        r.message = resultCode.getMessage();
        return r;
    }

    /** 自定义 code + message */
    public static <T> R<T> error(int code, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        return r;
    }

    /** 自定义 message（code 默认 500） */
    public static <T> R<T> error(String message) {
        return error(ResultCode.INTERNAL_ERROR.getCode(), message);
    }

    // ──────────────── 快捷错误 ────────────────

    /** 参数校验失败 */
    public static <T> R<T> badRequest(String message) {
        return error(ResultCode.BAD_REQUEST.getCode(),
                message != null ? message : ResultCode.BAD_REQUEST.getMessage());
    }

    /** 未登录 / Token 过期 */
    public static <T> R<T> unauthorized() {
        return error(ResultCode.UNAUTHORIZED);
    }

    /** 登录过期 */
    public static <T> R<T> tokenExpired() {
        return error(ResultCode.TOKEN_EXPIRED);
    }

    /** 无权限 */
    public static <T> R<T> forbidden() {
        return error(ResultCode.FORBIDDEN);
    }

    /** 资源不存在 */
    public static <T> R<T> notFound() {
        return error(ResultCode.NOT_FOUND);
    }

    /** 服务端错误（不允许抛给用户的） */
    public static <T> R<T> internalError() {
        return error(ResultCode.INTERNAL_ERROR);
    }

    // ──────────────── 带 traceId ────────────────

    public R<T> withTrace(String traceId) {
        this.traceId = traceId;
        return this;
    }

    // ──────────────── 判断 ────────────────

    public boolean isSuccess() {
        return this.code == ResultCode.SUCCESS.getCode();
    }
}