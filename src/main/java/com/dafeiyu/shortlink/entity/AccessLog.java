package com.dafeiyu.shortlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("access_log")
public class AccessLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private LocalDateTime accessTime;//记录的时间
    private String ip;
    private String userAgent;//用户使用的浏览器
    private String referer;//跳转到的地址
}
