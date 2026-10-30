package com.dafeiyu.shortlink.service;

import com.dafeiyu.shortlink.dto.ChangePasswordRequest;
import com.dafeiyu.shortlink.dto.LoginRequest;
import com.dafeiyu.shortlink.dto.LoginVO;
import com.dafeiyu.shortlink.dto.RegisterRequest;
import com.dafeiyu.shortlink.dto.UserVO;
import org.springframework.web.multipart.MultipartFile;

public interface AuthService {

    /** 注册成功直接返回 token，前端不用再登录一次 */
    LoginVO register(RegisterRequest request);

    LoginVO login(LoginRequest request);

    /** 退出：把这次 token 的 jti 拉黑，TTL 设成它自己剩下的寿命 */
    void logout(String token);

    UserVO me();

    void changePassword(ChangePasswordRequest request);

    /** 上传头像，返回可访问的相对地址（/uploads/avatar/xxx.png） */
    String uploadAvatar(MultipartFile file);
}
