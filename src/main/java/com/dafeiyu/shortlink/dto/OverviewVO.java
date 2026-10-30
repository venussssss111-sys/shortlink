package com.dafeiyu.shortlink.dto;

import lombok.Data;

/** 全局总览 */
@Data
public class OverviewVO {
    /** 短链总数 */
    private Long linkCount;
    /** 今天 PV / UV（来自 Redis） */
    private Long todayPv;
    private Long todayUv;
    /** 全历史 PV（MySQL 已落库的 + 今天的） */
    private Long totalPv;
}
