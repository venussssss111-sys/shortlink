package com.dafeiyu.shortlink.dto;

/** 限流结果：放不放行、还剩几张票、建议几秒后重试 */
public record RateLimitResult(boolean allowed, long remaining, long retryAfterSeconds) {

    public static RateLimitResult allow(long remaining) {
        return new RateLimitResult(true, remaining, 0);
    }
}

