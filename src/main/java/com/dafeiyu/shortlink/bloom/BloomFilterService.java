package com.dafeiyu.shortlink.bloom;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * 本地内存版布隆过滤器（Guava）。
 * <p>
 * 为什么不用 Redis 版：Redisson 的 RBloomFilter 每次 contains 要发 1 次 EXISTS
 * + 1 次 Lua 脚本 + 7 次 GETBIT（7 个哈希函数），压测实测每请求约 10 条 Redis 命令，
 * 吞吐从 1.8 万掉到 7 千。本地版直接读 JVM 内存，0 次网络往返。
 */
@Slf4j
@Service
public class BloomFilterService {

    /** 预计最多放多少个短码 */
    private static final long EXPECTED_INSERTIONS = 2_000_000L;

    /** 误判率：1%（100 个不存在的码里，最多 1 个被放行到 Redis） */
    private static final double FPP = 0.01;

    /**
     * volatile：重建时整体换掉这个引用。
     * 保证其他线程要么看到旧的一份、要么看到新的一份，不会看到"清空了一半"的中间状态。
     */
    private volatile BloomFilter<String> bloom;

    @PostConstruct
    public void init() {
        bloom = newBloom();
        log.info("布隆过滤器初始化完成（本地内存），预计容量={}，误判率={}", EXPECTED_INSERTIONS, FPP);
    }

    /** 建一个新的空布隆，给重建用 */
    public static BloomFilter<String> newBloom() {
        return BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                EXPECTED_INSERTIONS,
                FPP);
    }

    /** 往布隆里记一个短码 */
    public void add(String code) {
        bloom.put(code);
    }

    /**
     * 判断短码「有没有可能存在」。
     * 返回 false = 一定不存在（直接 404，不碰 Redis 也不碰 MySQL）。
     */
    public boolean mightExist(String code) {
        return bloom.mightContain(code);
    }

    /** 用一份新建好的布隆整体替换旧的（copy-on-write） */
    public void replaceWith(BloomFilter<String> fresh) {
        this.bloom = fresh;
    }

    public long approximateSize() {
        return bloom.approximateElementCount();
    }
}
