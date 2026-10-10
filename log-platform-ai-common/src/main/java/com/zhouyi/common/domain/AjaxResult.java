package com.zhouyi.common.domain;


/**
 * @author zjz
 * @version 1.0
 * @description: 请求返回
 * @date 2026/9/12 17:32
 */
public record AjaxResult<T>(
        /** HTTP 语义对应的业务状态码。 */
        int code,
        /** 面向用户的结果说明。 */
        String message,
        /** 成功时的业务数据。 */
        T data) {

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
