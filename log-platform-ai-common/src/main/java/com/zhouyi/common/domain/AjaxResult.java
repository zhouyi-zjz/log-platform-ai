package com.zhouyi.common.domain;


/**
 * @author zjz
 * @version 1.0
 * @description: 请求返回
 * @date 2026/9/12 17:32
 */
public record AjaxResult<T>(int code, String message, T data) {

    public static <T> AjaxResult<T> success(T data) {
        return success("操作成功", data);
    }

    public static <T> AjaxResult<T> success(String message, T data) {
        return new AjaxResult<>(200, message, data);
    }

    public static <T> AjaxResult<T> error(int code, String message) {
        return new AjaxResult<>(code, message, null);
    }
}
