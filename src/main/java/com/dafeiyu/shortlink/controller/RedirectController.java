package com.dafeiyu.shortlink.controller;

import com.dafeiyu.shortlink.common.BizException;
import com.dafeiyu.shortlink.entity.AccessLog;
import com.dafeiyu.shortlink.service.AccessLogCollector;
import com.dafeiyu.shortlink.service.MissGuard;
import com.dafeiyu.shortlink.service.ShortLinkService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDateTime;

/**
 * 短码跳转。
 *
 * 两个关键点：
 * 1. 必须用 302（FOUND）而不是 301。301 会被浏览器永久缓存，
 *    第二次点击不再经过服务器，访问统计就全丢了。
 * 2. 必须带 Cache-Control: no-store，否则中间层/浏览器缓存同样会导致统计丢失。
 */
@RestController
@RequiredArgsConstructor
public class RedirectController {
    private final AccessLogCollector accessLogCollector;
    private final MissGuard missGuard;

    /** 短码格式：4-10 位字母或数字 */
    private static final String CODE_PATTERN = "^[a-zA-Z0-9]{4,10}$";

    private final ShortLinkService shortLinkService;

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code , HttpServletRequest request) {
        // 不是短码格式的请求直接当 404，免得 /favicon.ico 之类的也去打库
        if (!code.matches(CODE_PATTERN)) {
            throw BizException.notFound("短链接不存在");
        }
        String originUrl = resolveOrGuard(code, request);
        AccessLog entry = new AccessLog();
        entry.setCode(code);
        entry.setAccessTime(LocalDateTime.now());
        entry.setIp(clientIp(request));
        entry.setUserAgent(truncate(request.getHeader("User-Agent"), 500));
        entry.setReferer(truncate(request.getHeader("Referer"), 500));
        accessLogCollector.submit(entry);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originUrl))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
    /**
     * 找不到就 404；但同一个 IP 一分钟内已经猜错太多次，就直接 429。
     *
     * 短码是随机的，正常用户几乎不会连续 miss；连续 miss 只可能是枚举扫描。
     * 注意只对 404 计数：410 是「曾经存在、后来停用/过期」，不该算猜错。
     */
    private String resolveOrGuard(String code, HttpServletRequest request) {
        try {
            return shortLinkService.resolve(code);
        } catch (BizException e) {
            if (e.getCode() == 404 && !missGuard.allow(clientIp(request))) {
                throw new BizException(429, "请求太频繁，请稍后重试");
            }
            throw e;
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();   // "1.2.3.4, 代理1" 取第一个
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String remote = request.getRemoteAddr();
        // 本机测试时会拿到 IPv6 回环，统一成 127.0.0.1，否则 UV 统计会被它干扰
        return "0:0:0:0:0:0:0:1".equals(remote) ? "127.0.0.1" : remote;
    }

    /** 截断，防止超长字符串拖垮后面的批量 INSERT */
    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
