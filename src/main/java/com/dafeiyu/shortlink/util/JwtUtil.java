package com.dafeiyu.shortlink.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 极简 JWT（HS256）。
 *
 * 没有引 jjwt，用 JDK 自带的 javax.crypto 手写，就三件事：
 *   token = base64url(header) + "." + base64url(payload) + "." + base64url(HmacSHA256(前两段, secret))
 *
 * 为什么不引库：JWT 本质是「签名 + base64」，看懂这 40 行比会调 API 值钱；
 * 面试问「JWT 能不能被篡改」，标准答法就是「改了 payload 签名就对不上，服务端验签失败」——
 * 这段代码就是那句话的实现。
 *
 * 注意：payload 只是 base64，不是加密，谁都能解开看。所以里面绝对不能放密码。
 */
public class JwtUtil {

    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();

    private final byte[] secret;
    private final long expireMinutes;
    private final ObjectMapper json = new ObjectMapper();

    public JwtUtil(String secret, long expireMinutes) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expireMinutes = expireMinutes;
    }

    /** 解析出来的载荷 */
    @Getter
    public static class Claims {
        private Long userId;
        private boolean admin;
        private String username;
        private String jti;
        /** 过期时间（epoch 秒） */
        private long expireAt;
    }

    public String sign(Long userId, String username) {
        return sign(userId, username, false);
    }

    public String sign(Long userId, String username, boolean admin) {
        long now = System.currentTimeMillis() / 1000;
        String header = ENC.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        // 用 Jackson 序列化，不用 String.format 拼 JSON：
        // 用户名里只要带一个双引号，拼出来的就不是合法 JSON，攻击者能借此塞进额外字段
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sub", userId);
        body.put("name", username);
        // 管理员标记放进令牌，鉴权时就不用每个请求都查一次 user 表
        body.put("adm", admin);
        body.put("iat", now);
        body.put("exp", now + expireMinutes * 60);
        body.put("jti", UUID.randomUUID().toString());
        String payload = ENC.encodeToString(writeJson(body).getBytes(StandardCharsets.UTF_8));
        String data = header + "." + payload;
        return data + "." + ENC.encodeToString(hmac(data));
    }

    /**
     * 验签 + 验过期。失败一律返回 null，由调用方统一转 401。
     * 不抛异常是为了不把「签名错」还是「过期了」这种细节泄露给攻击者。
     */
    public Claims verify(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        String data = parts[0] + "." + parts[1];
        byte[] actual;
        try {
            actual = DEC.decode(parts[2]);
        } catch (IllegalArgumentException e) {
            return null;
        }
        // 必须用常量时间比较：普通的 Arrays.equals 会在第一个不同字节就返回，
        // 攻击者能靠响应时间一位一位试出签名（时序攻击）。
        if (!MessageDigest.isEqual(hmac(data), actual)) {
            return null;
        }
        try {
            JsonNode node = json.readTree(DEC.decode(parts[1]));
            long exp = node.path("exp").asLong();
            if (exp * 1000 <= System.currentTimeMillis()) {
                return null;
            }
            Claims claims = new Claims();
            claims.userId = node.path("sub").asLong();
            claims.username = node.path("name").asText();
            claims.admin = node.path("adm").asBoolean(false);
            claims.jti = node.path("jti").asText();
            claims.expireAt = exp;
            return claims;
        } catch (Exception e) {
            return null;
        }
    }

    private String writeJson(Map<String, Object> body) {
        try {
            return json.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JWT payload 序列化失败", e);
        }
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 签名失败", e);
        }
    }
}
