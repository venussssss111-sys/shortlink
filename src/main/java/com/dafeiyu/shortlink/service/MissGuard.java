package com.dafeiyu.shortlink.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 枚举防护：同一个 IP 在一分钟窗口内「猜错」太多次，就先停它一会儿。
 *
 * 为什么需要它：
 *   短码是 6 位 Base62（约 568 亿种），正常的瞎猜几乎不可能命中；
 *   但攻击者可以持续扫——每一次 miss 都是一次真实查询（虽然布隆过滤器会挡掉大部分）。
 *   所以这里不按「请求数」限，而是按「miss 数」限：正常用户几乎不会 miss。
 *
 * 只对 404 计数，410（已停用/已过期）不算——那种码是真的存在过的。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissGuard {

    private static final String PREFIX = "guard:miss:";
    /** 一个窗口内最多允许多少次 404 */
    private static final int LIMIT = 30;
    private static final Duration WINDOW = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;

    public boolean allow(String ip) {
        try {
            // key 里带窗口编号：每分钟自动换一个新 key，不需要额外的清理任务
            String key = PREFIX + ip + ":" + (System.currentTimeMillis() / WINDOW.toMillis());
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, WINDOW);
            }
            return count == null || count <= LIMIT;
        } catch (Exception e) {
            // fail-open：统计是附加保护，Redis 抖一下不该让正常用户打不开短链
            log.warn("枚举防护计数失败，本次放行: ip={}", ip, e);
            return true;
        }
    }
}
