package com.dafeiyu.shortlink.util;

import com.dafeiyu.shortlink.common.BizException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 链接校验（开放重定向 + SSRF 的第一道闸）。
 *
 * 短链服务天生是「跳板」：别人拿你的域名发出去，受害者也看不出目标是谁。
 * 所以这里必须拦住四类东西：
 *   1. 非 http/https 协议   —— javascript: / data: / file: 会在受害者浏览器里执行
 *   2. user:pass@host 写法  —— 链接里真实域名在 @ 后面，前面那串是幌子（钓鱼最常用）
 *   3. 内网 / 回环地址       —— 否则你的服务器会替攻击者去探测 127.0.0.1、192.168.x、云厂商的 169.254.169.254
 *   4. 非常规端口           —— 防止被用来扫 http://x:22、http://x:6379 这类服务
 */
public final class UrlValidator {

    /** 只允许这两种协议 */
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    /** 只放行标准端口（-1 表示没写端口，即默认 80/443） */
    private static final Set<Integer> ALLOWED_PORTS = Set.of(-1, 80, 443);

    /** 内网、保留、家庭网段里常见的域名后缀 */
    private static final Set<String> INTERNAL_SUFFIXES =
            Set.of(".localhost", ".local", ".internal", ".intranet", ".lan", ".home.arpa");

    private static final int MAX_LENGTH = 2048;

    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    private UrlValidator() {
    }

    public static void validate(String url) {
        if (url == null || url.isBlank()) {
            throw BizException.badRequest("链接不能为空");
        }
        if (url.length() > MAX_LENGTH) {
            throw BizException.badRequest("链接长度不能超过 " + MAX_LENGTH);
        }

        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw BizException.badRequest("链接格式不合法");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw BizException.badRequest("只支持 http / https 链接");
        }

        // http://www.baidu.com@evil.com —— 浏览器实际会跳到 evil.com
        if (uri.getUserInfo() != null) {
            throw BizException.badRequest("链接不能包含用户名或密码");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw BizException.badRequest("链接缺少域名");
        }
        host = host.toLowerCase(Locale.ROOT);

        if (uri.getPort() != -1 && !ALLOWED_PORTS.contains(uri.getPort())) {
            throw BizException.badRequest("只支持 80 / 443 端口");
        }

        // IPv6 字面量在 URI 里带方括号，例如 [::1]
        if (host.startsWith("[")) {
            throw BizException.badRequest("不能使用 IP 地址或 IPv6 地址");
        }
        if (IPV4.matcher(host).matches()) {
            if (isPrivateIpv4(host)) {
                throw BizException.badRequest("不能使用内网或本机地址");
            }
            // 公网 IP 也拒：短链应该指向域名，允许裸 IP 等于给钓鱼留后门
            throw BizException.badRequest("不能使用 IP 地址，请填写域名");
        }

        if (host.equals("localhost") || host.endsWith(".localhost")) {
            throw BizException.badRequest("不能使用本机地址");
        }
        for (String suffix : INTERNAL_SUFFIXES) {
            if (host.endsWith(suffix)) {
                throw BizException.badRequest("不能使用内网域名");
            }
        }
        // 没有点的单段主机名（如 http://intranet/secret）只会解析到内网
        if (!host.contains(".")) {
            throw BizException.badRequest("域名格式不合法");
        }
    }

    private static boolean isPrivateIpv4(String host) {
        String[] parts = host.split("\\.");
        int a = Integer.parseInt(parts[0]);
        int b = Integer.parseInt(parts[1]);

        if (a > 255 || b > 255) {
            throw BizException.badRequest("域名格式不合法");
        }
        return a == 10                                  // 10.0.0.0/8
                || a == 127                             // 回环
                || a == 0                               // 0.0.0.0/8
                || (a == 172 && b >= 16 && b <= 31)     // 172.16.0.0/12
                || (a == 192 && b == 168)               // 192.168.0.0/16
                || (a == 169 && b == 254)               // 链路本地（含云厂商元数据 169.254.169.254）
                || (a == 100 && b >= 64 && b <= 127);   // CGNAT 100.64.0.0/10
    }
}
