package com.dafeiyu.shortlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dafeiyu.shortlink.dto.DailyStat;
import com.dafeiyu.shortlink.dto.TopLinkVO;
import com.dafeiyu.shortlink.entity.LinkStats;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

public interface LinkStatsMapper extends BaseMapper<LinkStats> {

    /**
     * 批量写入；如果 (code, stat_date) 已存在就更新 PV/UV。
     * 靠 UNIQUE KEY uk_code_date 触发 ON DUPLICATE KEY UPDATE。
     */
    @Insert("""
            <script>
            INSERT INTO link_stats (code, stat_date, pv, uv) VALUES
            <foreach collection="list" item="it" separator=",">
                (#{it.code}, #{it.statDate}, #{it.pv}, #{it.uv})
            </foreach>
            ON DUPLICATE KEY UPDATE pv = VALUES(pv), uv = VALUES(uv)
            </script>
            """)
    int upsertBatch(@Param("list") List<LinkStats> list);

    /**
     * 某个码在 today 之前的 PV 总和。
     * 不含今天——今天的数还在 Redis 里，由 Java 补上。
     */
    @Select("SELECT COALESCE(SUM(pv), 0) FROM link_stats WHERE code = #{code} AND stat_date < #{today}")
    Long sumPvBefore(@Param("code") String code, @Param("today") LocalDate today);

    /** 趋势：start <= stat_date < end，按日期升序 */
    @Select("""
            SELECT stat_date AS statDate, pv, uv
            FROM link_stats
            WHERE code = #{code} AND stat_date >= #{start} AND stat_date < #{end}
            ORDER BY stat_date
            """)
    List<DailyStat> listTrend(@Param("code") String code,
                              @Param("start") LocalDate start,
                              @Param("end") LocalDate end);

    /**
     * 排行榜：按窗口内 PV 之和倒序。
     * LEFT JOIN 是为了让短链被删掉后数据还在（originUrl 会是 null）。
     */
    @Select("""
            SELECT s.code AS code, l.origin_url AS originUrl, SUM(s.pv) AS pv, SUM(s.uv) AS uv
            FROM link_stats s
            LEFT JOIN short_link l ON l.code = s.code
            WHERE s.stat_date >= #{start}
              AND (#{userId} IS NULL OR l.user_id = #{userId})
            GROUP BY s.code, l.origin_url
            ORDER BY pv DESC
            LIMIT #{limit}
            """)
    List<TopLinkVO> listTop(@Param("start") LocalDate start,
                            @Param("limit") int limit,
                            @Param("userId") Long userId);

    /**
     * 全历史汇总（不含今天）。
     * 必须排除今天：今天是手工/定时聚合出来的，Redis 里还有一份更全的，
     * 如果这里也把今天算进来，Service 再把 Redis 的今天加上，就会重复计数。
     */
    @Select("""
            SELECT COALESCE(SUM(s.pv), 0) AS pv, COALESCE(SUM(s.uv), 0) AS uv
            FROM link_stats s
            LEFT JOIN short_link l ON l.code = s.code
            WHERE s.stat_date < CURDATE()
              AND (#{userId} IS NULL OR l.user_id = #{userId})
            """)
    TopLinkVO sumAll(@Param("userId") Long userId);
}
