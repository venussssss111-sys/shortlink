package com.dafeiyu.shortlink.common;

import lombok.Data;

/**
 * 统一返回结构。
 * 前端只需要判断 body.code == 200。
 */
@Data
public class Result<T> {

    /** 200 成功；400 参数错误；404 不存在；409 冲突；410 已过期；500 服务异常 */
    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 200;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
