package com.dafeiyu.shortlink.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {

    /** JWT，前端存 localStorage，之后每个请求带 Authorization: Bearer xxx */
    private String token;

    private UserVO user;
}
