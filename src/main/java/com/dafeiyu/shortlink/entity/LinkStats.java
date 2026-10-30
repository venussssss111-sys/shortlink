package com.dafeiyu.shortlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("link_stats")
//链接统计
public class LinkStats {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    //统计日期
    private LocalDate statDate;
    private Long pv;
    private Long uv;

}
