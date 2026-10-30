package com.dafeiyu.shortlink.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** 一天的数据点，给趋势图用 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyStat {
    private LocalDate statDate;
    private Long pv;
    private Long uv;
}
