package com.dafeiyu.shortlink.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 返回给前端的用户信息。
 * 特意没有 password / salt —— 实体绝不直接返回，这是最基本的防线。
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private LocalDateTime createdAt;
}
