package com.dafeiyu.shortlink.service;

import com.dafeiyu.shortlink.dto.DailyStat;
import com.dafeiyu.shortlink.dto.LinkStatsVO;
import com.dafeiyu.shortlink.dto.NameValueVO;
import com.dafeiyu.shortlink.dto.OverviewVO;
import com.dafeiyu.shortlink.dto.TopLinkVO;
import com.dafeiyu.shortlink.mapper.AccessLogMapper;
import com.dafeiyu.shortlink.mapper.LinkStatsMapper;
import com.dafeiyu.shortlink.mapper.ShortLinkMapper;
import com.dafeiyu.shortlink.util.RedisScanner;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.stream.Collectors;
import com.dafeiyu.shortlink.service.ShortLinkService;
import com.dafeiyu.shortlink.entity.ShortLink;
import com.dafeiyu.shortlink.common.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Set;

/**
 * 统计查询。
 *
 * 核心难点：数据在两个地方
 *   - 今天：还在 Redis（要等 00:05 才落库）
 *   - 历史：在 MySQL 的 link_stats 表
 * 查询时必须把两边合起来，否则「今天的数据」永远查不到。
 */
@Service
@RequiredArgsConstructor
public class StatsQueryService {

    private final StringRedisTemplate redisTemplate;
    private final LinkStatsMapper linkStatsMapper;
    private final ShortLinkMapper shortLinkMapper;
    private final AccessLogMapper accessLogMapper;
    private final RedisScanner redisScanner;
    private final ShortLinkService shortLinkService;

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String PV_PREFIX = "pv:";
    private static final String UV_PREFIX = "uv:";
    private static final int MAX_DAYS = 90;

    // ---------------------------------------------------------------- 单链统计

    public LinkStatsVO queryLink(String code, int days) {
        // 统计也是管理视角：不是自己的码，一律 404，别把别人的 PV/UV 漏出去
        shortLinkService.getOwnedByCode(code);
        int d = Math.max(1, Math.min(days, MAX_DAYS));
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(d - 1L);

        // 今天：Redis
        long todayPv = readPv(code, today);
        long todayUv = readUv(code, today);

        // 历史：MySQL（不含今天）
        long historyPv = nz(linkStatsMapper.sumPvBefore(code, today));
        List<DailyStat> trend = new ArrayList<>(linkStatsMapper.listTrend(code, start, today));

        // 今天也拼进趋势里
        if (todayPv > 0 || todayUv > 0) {
            trend.add(new DailyStat(today, todayPv, todayUv));
        }

        long recentPv = 0L;
        for (DailyStat s : trend) {
            recentPv += nz(s.getPv());
        }
        // 窗口 UV 必须用 HyperLogLog 并集去重，不能把各天 uv 相加n
        long recentUv = unionUv(code, trend);

        LinkStatsVO vo = new LinkStatsVO();
        vo.setCode(code);
        vo.setTodayPv(todayPv);
        vo.setTodayUv(todayUv);
        vo.setRecentDays(d);
        vo.setRecentPv(recentPv);
        vo.setRecentUv(recentUv);
        vo.setTotalPv(historyPv + todayPv);
        vo.setTrend(trend);
        return vo;
    }

    // ------------------------------------------------------------------ 总览

    public OverviewVO overview() {
        LocalDate today = LocalDate.now();
        String dayPart = today.format(DAY);

        Long uid = scopeUserId();
        // 普通用户只统计自己的码；管理员看全部（myCodes 为 null 表示不过滤）
        Set<String> myCodes = uid == null ? null : ownCodes(uid);

        Set<String> pvKeys = redisScanner.scan(PV_PREFIX + "*:" + dayPart);
        long todayPv = 0L;
        List<String> uvKeys = new ArrayList<>();
        for (String pvKey : pvKeys) {
            String code = codeOf(pvKey, dayPart);
            if (myCodes != null && !myCodes.contains(code)) {
                continue;
            }
            String value = redisTemplate.opsForValue().get(pvKey);
            if (value != null) {
                todayPv += Long.parseLong(value);
            }
            uvKeys.add(UV_PREFIX + code + ":" + dayPart);
        }
        long todayUv = uvKeys.isEmpty()
                ? 0L
                : nz(redisTemplate.opsForHyperLogLog().size(uvKeys.toArray(new String[0])));

        TopLinkVO history = linkStatsMapper.sumAll(uid);
        long historyPv = history == null ? 0L : nz(history.getPv());

        OverviewVO vo = new OverviewVO();
        vo.setLinkCount(shortLinkMapper.selectCount(
                new LambdaQueryWrapper<ShortLink>().eq(uid != null, ShortLink::getUserId, uid)));
        vo.setTodayPv(todayPv);
        vo.setTodayUv(todayUv);
        vo.setTotalPv(historyPv + todayPv);
        return vo;
    }

    // ---------------------------------------------------------------- 排行榜

    public List<TopLinkVO> listTop(int days, int limit) {
        int d = Math.max(1, Math.min(days, MAX_DAYS));
        int n = Math.max(1, Math.min(limit, 100));
        LocalDate start = LocalDate.now().minusDays(d - 1L);
        return linkStatsMapper.listTop(start, n, scopeUserId());
    }

    // -------------------------------------------------------------- 来源 / 设备

    /**
     * 来源分布。数据取自 access_log（不是 link_stats）：
     * 因为 referer 太散，没必要进聚合表，查的时候现场 group by 就行。
     */
    public List<NameValueVO> listSources(int days) {
        int d = Math.max(1, Math.min(days, MAX_DAYS));
        LocalDateTime start = LocalDate.now().minusDays(d - 1L).atStartOfDay();
        return accessLogMapper.listSources(start, 8, scopeUserId());
    }

    /** 设备分布 */
    public List<NameValueVO> listDevices(int days) {
        int d = Math.max(1, Math.min(days, MAX_DAYS));
        LocalDateTime start = LocalDate.now().minusDays(d - 1L).atStartOfDay();
        return accessLogMapper.listDevices(start, scopeUserId());
    }

    // ---------------------------------------------------------------- 私有工具

    /** 普通用户只看自己的数据；管理员不限（返回 null 表示不加条件） */
    private Long scopeUserId() {
        return UserContext.isAdmin() ? null : UserContext.get();
    }

    /** 当前用户拥有的短码集合，用于过滤 Redis 里按 code 分散的 PV/UV key */
    private Set<String> ownCodes(Long userId) {
        return shortLinkMapper.selectList(new LambdaQueryWrapper<ShortLink>()
                        .select(ShortLink::getCode)
                        .eq(ShortLink::getUserId, userId))
                .stream()
                .map(ShortLink::getCode)
                .collect(Collectors.toSet());
    }

    private long readPv(String code, LocalDate day) {
        String value = redisTemplate.opsForValue().get(PV_PREFIX + code + ":" + day.format(DAY));
        return value == null ? 0L : Long.parseLong(value);
    }

    private long readUv(String code, LocalDate day) {
        Long uv = redisTemplate.opsForHyperLogLog().size(UV_PREFIX + code + ":" + day.format(DAY));
        return nz(uv);
    }

    /** 把窗口内每一天的 uv key 并起来算基数——同一个人跨天只算一次 */
    private long unionUv(String code, List<DailyStat> trend) {
        if (trend.isEmpty()) {
            return 0L;
        }
        String[] keys = new String[trend.size()];
        for (int i = 0; i < trend.size(); i++) {
            keys[i] = UV_PREFIX + code + ":" + trend.get(i).getStatDate().format(DAY);
        }
        return nz(redisTemplate.opsForHyperLogLog().size(keys));
    }

    /** pv:V0002:20261027 -> V0002 */
    private static String codeOf(String pvKey, String dayPart) {
        return pvKey.substring(PV_PREFIX.length(), pvKey.length() - dayPart.length() - 1);
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }
}
