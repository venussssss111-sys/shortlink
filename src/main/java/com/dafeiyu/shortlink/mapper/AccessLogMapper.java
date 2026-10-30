package com.dafeiyu.shortlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dafeiyu.shortlink.dto.NameValueVO;
import com.dafeiyu.shortlink.entity.AccessLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface AccessLogMapper extends BaseMapper<AccessLog> {

    @Insert("""
            <script>
            INSERT INTO access_log (code, access_time, ip, user_agent, referer) VALUES
            <foreach collection="list" item="it" separator=",">
                (#{it.code}, #{it.accessTime}, #{it.ip}, #{it.userAgent}, #{it.referer})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("list") List<AccessLog> list);

    /**
     * 来源分布：把 referer 拆出域名再分组。
     *   https://www.baidu.com/s?wd=x  →  www.baidu.com
     *   SUBSTRING_INDEX(referer, '/', 3) 取前 3 段 → https://www.baidu.com
     *   SUBSTRING_INDEX(..., '//', -1)   取最后一段 → www.baidu.com
     */
    @Select("""
            SELECT CASE
                     WHEN referer IS NULL OR referer = '' THEN '直接访问'
                     ELSE SUBSTRING_INDEX(SUBSTRING_INDEX(referer, '/', 3), '//', -1)
                   END AS `name`,
                   COUNT(*) AS `value`
            FROM access_log a
            LEFT JOIN short_link l ON l.code = a.code
            WHERE a.access_time >= #{start}
              AND (#{userId} IS NULL OR l.user_id = #{userId})
            GROUP BY `name`
            ORDER BY `value` DESC
            LIMIT #{limit}
            """)
    List<NameValueVO> listSources(@Param("start") LocalDateTime start,
                                  @Param("limit") int limit,
                                  @Param("userId") Long userId);

    /**
     * 设备分布：用 UA 里的关键字猜。注意表是 utf8mb4_bin（区分大小写），
     * 所以要先 LOWER() 再 LIKE，否则 "Mobile" 匹配不到 "mobile"。
     * 判断顺序很重要：先爬虫，再平板，最后手机 —— iPad 的 UA 里同时含 iPad 和 Mobile。
     */
    @Select("""
            SELECT CASE
                     WHEN user_agent IS NULL OR user_agent = '' THEN '未知'
                     WHEN LOWER(user_agent) LIKE '%bot%'
                       OR LOWER(user_agent) LIKE '%spider%'
                       OR LOWER(user_agent) LIKE '%crawl%' THEN '爬虫'
                     WHEN LOWER(user_agent) LIKE '%ipad%'
                       OR LOWER(user_agent) LIKE '%tablet%' THEN '平板'
                     WHEN LOWER(user_agent) LIKE '%mobile%'
                       OR LOWER(user_agent) LIKE '%android%'
                       OR LOWER(user_agent) LIKE '%iphone%' THEN '移动端'
                     ELSE '桌面端'
                   END AS `name`,
                   COUNT(*) AS `value`
            FROM access_log a
            LEFT JOIN short_link l ON l.code = a.code
            WHERE a.access_time >= #{start}
              AND (#{userId} IS NULL OR l.user_id = #{userId})
            GROUP BY `name`
            ORDER BY `value` DESC
            """)
    List<NameValueVO> listDevices(@Param("start") LocalDateTime start,
                                  @Param("userId") Long userId);
}
