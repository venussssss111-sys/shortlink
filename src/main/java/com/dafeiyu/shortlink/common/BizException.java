package com.dafeiyu.shortlink.common;

import lombok.Getter;

/**
 * 业务异常。抛出后由 GlobalExceptionHandler 转成对应 HTTP 状态码 + JSON。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException badRequest(String message) {
        return new BizException(400, message);
    }

    public static BizException notFound(String message) {
        return new BizException(404, message);
    }

    public static BizException conflict(String message) {
        return new BizException(409, message);
    }

    public static BizException gone(String message) {
        return new BizException(410, message);
    }

    public static BizException unauthorized(String message) {
        return new BizException(401, message);
    }
}
