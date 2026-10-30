package com.dafeiyu.shortlink.dto;

import lombok.Data;

import java.util.List;

/** 单个短链的统计 */
@Data
public class LinkStatsVO {
    private String code;

    /** 今天的 PV / UV（来自 Redis，还没落库） */
    private Long todayPv;
    private Long todayUv;

    /** 查询窗口的天数 */
    private Integer recentDays;
    /** 窗口内 PV 之和 */
    private Long recentPv;
    /** 窗口内去重后的 UV（HyperLogLog 并集，不是各天相加） */
    private Long recentUv;

    /** 全历史 PV */
    private Long totalPv;

    /** 每天一个点，按日期升序 */
    private List<DailyStat> trend;
}
