package com.dafeiyu.shortlink.util;

import java.security.SecureRandom;

/**
 * Base62 编解码工具。
 *
 * 提供两种短码生成思路，面试时要能讲出取舍：
 *   encode(num)   —— 自增 ID 转 62 进制：绝对不冲突，但可枚举，会暴露业务量
 *   random(len)   —— 随机字符：不可枚举，有碰撞概率，靠数据库唯一索引兜底
 *
 * 本项目默认用 random。
 */
public final class Base62 {

    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private static final int BASE = ALPHABET.length;

    private static final SecureRandom RANDOM = new SecureRandom();

    private Base62() {
    }

    /**
     * 十进制转 62 进制。
     * 例：1 -> "1"，62 -> "10"，3844 -> "100"
     */
    public static String encode(long num) {
        if (num < 0) {
            throw new IllegalArgumentException("num must be >= 0");
        }
        if (num == 0) {
            return String.valueOf(ALPHABET[0]);
        }
        StringBuilder sb = new StringBuilder(11);
        while (num > 0) {
            sb.append(ALPHABET[(int) (num % BASE)]);
            num /= BASE;
        }
        return sb.reverse().toString();
    }

    /**
     * 随机生成指定长度的短码。
     * 6 位约有 62^6 ≈ 568 亿种组合。
     */
    public static String random(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(BASE)]);
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        System.out.println("encode(1)   = " + encode(1));
        System.out.println("encode(62)  = " + encode(62));
        System.out.println("encode(3844)= " + encode(3844));
        System.out.println("random(6)   = " + random(6));
    }
}
