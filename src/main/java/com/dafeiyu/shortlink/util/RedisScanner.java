package com.dafeiyu.shortlink.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 按 pattern 扫 key。
 *
 * 为什么不用 redisTemplate.keys(pattern)：
 * KEYS 是 O(N) 且会阻塞 Redis 单线程，扫大库时所有请求排队；
 * SCAN 是游标分批扫，扫的过程中其他命令能插队执行。
 */
@Component
@RequiredArgsConstructor
public class RedisScanner {

    private final StringRedisTemplate redisTemplate;

    public Set<String> scan(String pattern) {
        Set<String> result = new LinkedHashSet<>();
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(500).build();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    result.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return null;
        });
        return result;
    }
}
