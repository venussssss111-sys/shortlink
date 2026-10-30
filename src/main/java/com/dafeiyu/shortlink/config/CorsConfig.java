package com.dafeiyu.shortlink.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置。
 *
 * 为什么部署到 nginx 之后还要留着它：
 * 浏览器发 POST 时**即使同源也会带 Origin 头**，Spring 只要看到 Origin 就走 CORS 校验，
 * origin 不在白名单里会直接回 403 "Invalid CORS request"。
 * 表现就是「curl 能登录，浏览器点登录 403」—— 因为 curl 默认不带 Origin。
 *
 * 白名单从配置读：shortlink.cors.allowed-origins，逗号分隔。
 * 生产环境换成你的域名，不要写 *（配了 * 又带凭证会报错，容易踩坑）。
 */
@Slf4j
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${shortlink.cors.allowed-origins:http://localhost:5173,http://localhost:8088}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins.split("\\s*,\\s*");
        log.info("CORS 允许的来源: {}", String.join(", ", origins));
        registry.addMapping("/api/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // 让浏览器里的 JS 能读到限流相关响应头
                .exposedHeaders("X-RateLimit-Remaining", "X-RateLimit-Scope", "Retry-After")
                .maxAge(3600);
    }
}
