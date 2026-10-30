package com.dafeiyu.shortlink.service;

import com.dafeiyu.shortlink.entity.LinkStats;
import com.dafeiyu.shortlink.mapper.LinkStatsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsAggregateService {

    private final StringRedisTemplate redisTemplate;
    private final LinkStatsMapper linkStatsMapper;

    private static final String PV_PREFIX = "pv:";
    private static final String UV_PREFIX = "uv:";
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE; // 20261026

    /** 每天 00:05 把「昨天」的数据落库 */
    @Scheduled(cron = "0 5 0 * * ?")
    public void aggregateYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        int count = aggregate(yesterday);
        log.info("统计聚合完成 date={} 短码数={}", yesterday, count);
    }

    /**
     * 把某一天的 Redis 计数落进 link_stats。可重复执行（幂等）。
     * @return 落库的短码个数
     */
    public int aggregate(LocalDate day) {
        String dayPart = day.format(DAY);
        Set<String> pvKeys = scan(PV_PREFIX + "*:" + dayPart);
        if (pvKeys.isEmpty()) {
            log.info("没有需要聚合的数据 date={}", day);
            return 0;
        }

        List<LinkStats> list = new ArrayList<>();
        for (String pvKey : pvKeys) {
            // pv:V0002:20261026 -> V0002
            String code = pvKey.substring(PV_PREFIX.length(), pvKey.length() - dayPart.length() - 1);
            String pvValue = redisTemplate.opsForValue().get(pvKey);
            long pv = pvValue == null ? 0 : Long.parseLong(pvValue);
            Long uv = redisTemplate.opsForHyperLogLog().size(UV_PREFIX + code + ":" + dayPart);

            LinkStats stats = new LinkStats();
            stats.setCode(code);
            stats.setStatDate(day);
            stats.setPv(pv);
            stats.setUv(uv == null ? 0L : uv);
            list.add(stats);
        }

        int rows = linkStatsMapper.upsertBatch(list);
        log.info("落库完成 date={} 短码数={} 影响行数={}", day, list.size(), rows);
        return list.size();
    }

    /**
     * 用 SCAN 而不是 keys：KEYS 会遍历全部 key 且阻塞 Redis 单线程，
     * SCAN 是游标分批扫，每次只处理一小撮。
     */
    private Set<String> scan(String pattern) {
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