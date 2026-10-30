package com.dafeiyu.shortlink.service;

import com.dafeiyu.shortlink.config.RateLimitProperties;
import com.dafeiyu.shortlink.dto.RateLimitResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> tokenBucketScript;

    /**
     * 取一张令牌。
     *
     * Redis 出问题时按 fail-open 处理：放行 + 打一条 warn。
     * 理由：限流是「附加保护」而不是核心依赖。Redis 挂了，缓存和统计本来就已经受影响，
     * 这时候再把所有请求拒掉，等于主动把整个服务按死。
     */
    public RateLimitResult tryAcquire(String key, RateLimitProperties.Bucket bucket) {
        try {
            List<?> result = redisTemplate.execute(
                    tokenBucketScript,
                    List.of(key),
                    String.valueOf(bucket.getCapacity()),
                    String.valueOf(bucket.getRate()),
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(bucket.getTtlSeconds())
            );

            if (result == null || result.size() < 3) {
                // 脚本返回为空只可能是环境异常，同样按放行处理
                return RateLimitResult.allow(0);
            }
            return new RateLimitResult(
                    toLong(result.get(0)) == 1L,
                    toLong(result.get(1)),
                    toLong(result.get(2))
            );
        } catch (Exception e) {
            log.warn("限流脚本执行失败，本次放行（fail-open）: key={}", key, e);
            return RateLimitResult.allow(0);
        }
    }

    private static long toLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }
}
