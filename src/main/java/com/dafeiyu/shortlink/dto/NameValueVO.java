package com.dafeiyu.shortlink.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用的「名称 + 数量」，给饼图 / 柱状图用。
 * ECharts 的饼图数据格式就是 [{name, value}]，字段名故意保持一致。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NameValueVO {

    private String name;

    private Long value;
}
