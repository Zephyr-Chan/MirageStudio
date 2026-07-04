package com.mirage.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应封装
 *
 * @param <T> 业务数据类型
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务状态码: 0 成功, 非0 失败 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    /** 是否成功 */
    private boolean success;

    private R() {
    }

    private R(int code, String message, T data, boolean success) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.success = success;
    }

    public static <T> R<T> ok() {
        return new R<>(0, "success", null, true);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(0, "success", data, true);
    }

    public static <T> R<T> ok(String message, T data) {
        return new R<>(0, message, data, true);
    }

    public static <T> R<T> fail(String message) {
        return new R<>(-1, message, null, false);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null, false);
    }

    public static <T> R<T> fail(int code, String message, T data) {
        return new R<>(code, message, data, false);
    }
}
