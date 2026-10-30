package com.dafeiyu.shortlink.bloom;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dafeiyu.shortlink.entity.ShortLink;
import com.dafeiyu.shortlink.mapper.ShortLinkMapper;
import com.google.common.hash.BloomFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 布隆过滤器的预热与重建。
 * <p>
 * 本地布隆的唯一硬伤：多实例之间不同步。A 实例新建的短码，B 实例的布隆里没有，
 * 那么 B 上访问这个新码会被误判成「一定不存在」→ 404。
 * <p>
 * 这里的兜法是每 30 秒从库里全量重建一次，把窗口压到 30 秒以内。
 * 更严格的做法是 create 成功后往 Redis 发广播，各实例收到就 add —— 代价是多一个依赖。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BloomWarmUp implements CommandLineRunner {

    /** 重建间隔（毫秒） */
    private static final long REBUILD_INTERVAL_MS = 30_000L;

    private final BloomFilterService bloomFilterService;
    private final ShortLinkMapper shortLinkMapper;

    /** 启动时先灌一次，否则库里所有真实短码都会被判成不存在 */
    @Override
    public void run(String... args) {
        rebuild();
    }

    /**
     * 全量重建：先建一个新的空布隆，灌完所有 code，最后整体替换。
     * <p>
     * 注意顺序：绝不能对正在使用的那个 clear() 再重新 put，
     * 那样重建期间所有真实短码都会 404。
     */
    @Scheduled(fixedDelay = REBUILD_INTERVAL_MS, initialDelay = REBUILD_INTERVAL_MS)
    public void rebuild() {
        long start = System.currentTimeMillis();
        try {
            BloomFilter<String> fresh = BloomFilterService.newBloom();

            // 只查 code 一列，不要把整行拉出来
            List<ShortLink> all = shortLinkMapper.selectList(
                    new QueryWrapper<ShortLink>().select("code"));

            for (ShortLink link : all) {
                if (link.getCode() != null) {
                    fresh.put(link.getCode());
                }
            }

            // 最后一步才换上去：读线程要么看到旧的完整版，要么看到新的完整版
            bloomFilterService.replaceWith(fresh);

            log.info("布隆重建完成，共 {} 条，耗时 {} ms", all.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            // 重建失败就继续用旧的那份，不能让它影响正常请求
            log.warn("布隆重建失败，继续使用旧的一份", e);
        }
    }
}
