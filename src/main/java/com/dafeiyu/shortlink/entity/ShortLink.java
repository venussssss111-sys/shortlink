package com.dafeiyu.shortlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短链主表实体，对应 sql/schema.sql 里的 short_link。
 */
@Data
@TableName("short_link")
public class ShortLink {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 创建者 user.id。NULL 只可能出现在 v7 之前的历史数据里 */
    private Long userId;

    /** 短码，区分大小写（数据库排序规则为 utf8mb4_bin） */
    private String code;

    /** 原始长链接 */
    private String originUrl;

    /** 过期时间，null 表示永久 */
    private LocalDateTime expireTime;

    /** 1 正常，0 停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
