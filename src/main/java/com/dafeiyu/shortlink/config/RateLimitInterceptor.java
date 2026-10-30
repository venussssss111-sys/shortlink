package com.dafeiyu.shortlink.config;

import com.dafeiyu.shortlink.common.UserContext;
import com.dafeiyu.shortlink.dto.RateLimitResult;
import com.dafeiyu.shortlink.service.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 令牌桶限流拦截器。
 *
 * 位置：排在 AuthInterceptor 之后（order = 2），因为限流的 key 想优先用 userId，
 * 而 userId 是 AuthInterceptor 放进 ThreadLocal 的。order 反了就全按 IP 限，登录用户会互相误伤。
 *
 * 策略：拿不到令牌直接返回 429，绝不让请求排队等待。
 * Tomcat 默认 200 个工作线程，让请求"等令牌"会把线程占满，最终连 429 都返回不出去。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String KEY_PREFIX = "rl:";

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!properties.isEnabled()) {
            return true;
        }
        // 浏览器的跨域预检不带 token，也不该计入配额
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String scope = scopeOf(request);
        RateLimitResult result = rateLimiter.tryAcquire(
                buildKey(scope, request),
                bucketOf(scope)
        );

        if (result.allowed()) {
            // 把剩余令牌数带回去，前端/压测脚本能看到桶在怎么变
            response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
            return true;
        }

        reject(response, scope, result.retryAfterSeconds());
        return false;
    }

    /** 选桶：登录/注册走 auth，GET 走 read，其余（POST/PATCH/DELETE）走 write */
    private String scopeOf(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.endsWith("/api/auth/login") || path.endsWith("/api/auth/register")) {
            return "auth";
        }
        // 建链是唯一会写库的接口，单独一个桶（防刷短码）
        if (HttpMethod.POST.matches(request.getMethod()) && path.endsWith("/api/links")) {
            return "create";
        }
        return HttpMethod.GET.matches(request.getMethod()) ? "read" : "write";
    }

    private RateLimitProperties.Bucket bucketOf(String scope) {
        return switch (scope) {
            case "auth" -> properties.getAuth();
            case "create" -> properties.getCreate();
            case "read" -> properties.getRead();
            default -> properties.getWrite();
        };
    }

    /**
     * 生成桶的 key。
     * 已登录按用户（一个用户一个桶，不会误伤别人）；
     * 匿名按 IP（登录/注册接口走这里，防密码爆破）。
     */
    private String buildKey(String scope, HttpServletRequest request) {
        Long userId = UserContext.get();
        if (userId != null) {
            return KEY_PREFIX + "user:" + scope + ":" + userId;
        }
        return KEY_PREFIX + "ip:" + scope + ":" + clientIp(request);
    }

    /**
     * 取客户端 IP。
     *
     * X-Forwarded-For 是代理链追加的，第一段才是真实客户端；但它是客户端可以自己伪造的头，
     * 所以生产上只在「前面确实有自己的 Nginx」时才该信任它（Nginx 里用 proxy_set_header 覆盖，
     * 而不是 append）。本地开发直接读没问题。
     */
    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static void reject(HttpServletResponse response, String scope, long retryAfterSeconds) throws IOException {
        // Retry-After 单位是秒，且至少 1：填 0 客户端会立刻重试，等于没限流
        long retry = Math.max(1, retryAfterSeconds);
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retry));
        response.setHeader("X-RateLimit-Scope", scope);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":429,\"message\":\"请求太频繁，请 " + retry + " 秒后重试\",\"data\":null}"
        );
        log.debug("限流命中: scope={}, retryAfter={}s", scope, retry);
    }
}
