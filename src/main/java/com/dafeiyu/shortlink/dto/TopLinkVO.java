package com.dafeiyu.shortlink.dto;

import lombok.Data;

/** 排行榜的一行 */
@Data
public class TopLinkVO {
    private String code;
    private String originUrl;
    /** 窗口内 PV 之和 */
    private Long pv;
    /** 窗口内各天 UV 相加（会重复计数同一个人，仅作参考） */
    private Long uv;
}
