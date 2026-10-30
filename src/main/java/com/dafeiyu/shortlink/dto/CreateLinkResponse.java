package com.dafeiyu.shortlink.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建短链的返回结果。
 */
@Data
@Builder
@AllArgsConstructor
public class CreateLinkResponse {

    private String code;

    /** 可直接复制到浏览器的完整短链 */
    private String shortUrl;

    private String originUrl;

    private LocalDateTime expireTime;
}
