package com.dafeiyu.shortlink.config;

import com.dafeiyu.shortlink.common.UserContext;
import com.dafeiyu.shortlink.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 登录拦截器：/api/** 全部要求带合法 token（登录、注册除外，见 WebConfig）。
 *
 * 为什么用拦截器而不是 Spring Security：
 *   这个项目只需要「有没有登录」，不需要角色、权限表达式那一套；
 *   自己写一遍拦截器 + ThreadLocal，比调 Security 更能讲清楚原理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BLACK_PREFIX = "auth:black:";
    private static final String BEARER = "Bearer ";

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // 浏览器的跨域预检不带 token，必须放行，否则前端所有请求都会被 401
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String token = bearer(request.getHeader("Authorization"));
        JwtUtil.Claims claims = jwtUtil.verify(token);
        if (claims == null) {
            reject(response, "未登录或登录已过期");
            return false;
        }
        // 已退出的 token：签名还没过期，但不能再用了
        if (isBlacklisted(claims.getJti())) {
            reject(response, "登录已失效，请重新登录");
            return false;
        }
        UserContext.set(claims.getUserId(), claims.isAdmin());
        return true;
    }

    /**
     * 请求结束一定要清 ThreadLocal。
     * Tomcat 线程是复用的，不清的话下一个请求可能读到上一个用户的 id。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    /**
     * token 是否已被拉黑（用户点了退出）。
     *
     * Redis 挂掉时按「未拉黑」放行（fail-open）：否则 Redis 抖一下，全站所有请求都 500。
     * 代价是：Redis 不可用期间，已退出的 token 在过期前还能用。这是可用性和安全性的取舍，
     * 我认为「短时间可重放」远好于「全站不可用」。
     */
    private boolean isBlacklisted(String jti) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(BLACK_PREFIX + jti));
        } catch (Exception e) {
            log.warn("黑名单查询失败，按未拉黑处理: jti={}", jti, e);
            return false;
        }
    }

    private static String bearer(String header) {
        if (header == null) {
            return null;
        }
        return header.startsWith(BEARER) ? header.substring(BEARER.length()).trim() : header.trim();
    }

    private static void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\",\"data\":null}");
    }
}
