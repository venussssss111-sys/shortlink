package com.dafeiyu.shortlink.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建短链的请求参数。
 * 校验注解只负责"格式"；业务规则（比如协议白名单）在 UrlValidator 里做。
 */
@Data
public class CreateLinkRequest {

    @NotBlank(message = "不能为空")
    @Size(max = 2048, message = "长度不能超过 2048")
    private String originUrl;

    /** 不传则随机生成 6 位短码 */
    @Pattern(regexp = "^[a-zA-Z0-9]{4,10}$", message = "只能是 4-10 位字母或数字")
    private String customCode;

    /** 不传表示永久有效 */
    @Min(value = 1, message = "至少 1 天")
    @Max(value = 3650, message = "最多 3650 天")
    private Integer expireDays;
}
