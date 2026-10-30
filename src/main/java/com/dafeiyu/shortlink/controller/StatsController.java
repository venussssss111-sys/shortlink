package com.dafeiyu.shortlink.controller;

import com.dafeiyu.shortlink.common.Result;
import com.dafeiyu.shortlink.dto.LinkStatsVO;
import com.dafeiyu.shortlink.dto.NameValueVO;
import com.dafeiyu.shortlink.dto.OverviewVO;
import com.dafeiyu.shortlink.dto.TopLinkVO;
import com.dafeiyu.shortlink.service.StatsAggregateService;
import com.dafeiyu.shortlink.service.StatsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 统计相关接口。
 *
 * 单链统计的路径用 /link/{code}：
 * 如果写成 /{code}，那么 /overview、/top 也要靠 Spring 的「字面量优先」才能不被吃掉，
 * 显式分开更稳，而且以后加 /day/{code} 之类也不会冲突。
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsAggregateService statsAggregateService;
    private final StatsQueryService statsQueryService;

    /** POST /api/stats/aggregate?date=2026-10-26，不传 date 就聚合今天 */
    @PostMapping("/aggregate")
    public Result<Integer> aggregate(@RequestParam(required = false) String date) {
        LocalDate day = (date == null || date.isBlank()) ? LocalDate.now() : LocalDate.parse(date);
        return Result.ok(statsAggregateService.aggregate(day));
    }

    /** GET /api/stats/link/V0002?days=7 —— 单链统计 + 趋势 */
    @GetMapping("/link/{code}")
    public Result<LinkStatsVO> linkStats(@PathVariable String code,
                                         @RequestParam(defaultValue = "7") int days) {
        return Result.ok(statsQueryService.queryLink(code, days));
    }

    /** GET /api/stats/overview —— 全局总览 */
    @GetMapping("/overview")
    public Result<OverviewVO> overview() {
        return Result.ok(statsQueryService.overview());
    }

    /** GET /api/stats/top?days=7&limit=5 —— 排行榜 */
    @GetMapping("/top")
    public Result<List<TopLinkVO>> top(@RequestParam(defaultValue = "7") int days,
                                       @RequestParam(defaultValue = "5") int limit) {
        return Result.ok(statsQueryService.listTop(days, limit));
    }

    /** GET /api/stats/sources?days=7 —— 来源分布（饼图） */
    @GetMapping("/sources")
    public Result<List<NameValueVO>> sources(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(statsQueryService.listSources(days));
    }

    /** GET /api/stats/devices?days=7 —— 设备分布（柱状图） */
    @GetMapping("/devices")
    public Result<List<NameValueVO>> devices(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(statsQueryService.listDevices(days));
    }
}
