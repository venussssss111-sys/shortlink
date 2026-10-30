package com.dafeiyu.shortlink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,20}$", message = "只能是 3-20 位字母、数字或下划线")
    private String username;

    @NotBlank(message = "不能为空")
    @Size(min = 6, max = 32, message = "长度要在 6-32 位之间")
    private String password;

    @Size(max = 32, message = "最长 32 个字符")
    private String nickname;

    @Size(max = 64, message = "最长 64 个字符")
    private String email;
}
