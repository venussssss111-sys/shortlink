package com.dafeiyu.shortlink.config;

import com.dafeiyu.shortlink.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    /**
     * 密钥从配置读，不写死在代码里。
     * 生产环境用环境变量注入（export SHORTLINK_JWT_SECRET=...），不要提交进 git。
     */
    @Bean
    public JwtUtil jwtUtil(@Value("${shortlink.jwt.secret}") String secret,
                           @Value("${shortlink.jwt.expire-minutes:1440}") long expireMinutes) {
        return new JwtUtil(secret, expireMinutes);
    }
}
