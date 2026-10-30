package com.dafeiyu.shortlink.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "shortlink.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private Bucket read  = new Bucket();
    private Bucket write = new Bucket();
    private Bucket auth  = new Bucket();
    private Bucket create = new Bucket();

    @Data
    public static class Bucket {
        private int capacity = 60;      // 容量
        private double rate = 5.0;      // 每秒补充
        private long ttlSeconds = 60;   // key 存活时间
    }
}
