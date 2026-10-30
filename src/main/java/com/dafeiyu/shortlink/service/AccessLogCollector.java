package com.dafeiyu.shortlink.service;
import com.dafeiyu.shortlink.entity.AccessLog;
import com.dafeiyu.shortlink.mapper.AccessLogMapper;
import com.dafeiyu.shortlink.mapper.ShortLinkMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;




@Component
@Slf4j
@RequiredArgsConstructor
public class AccessLogCollector {
    private final StringRedisTemplate redisTemplate;
    private static final DateTimeFormatter DAY_FORMAT=DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String PV_PREFIX = "pv:";
    private static final String UV_PREFIX = "uv:";
    private static final Duration STATS_TTL = Duration.ofDays(35);
    private final AccessLogMapper accessLogMapper;
    //队列容量
    private static final int QUEUE_CAPACITY =10000;
    //一次最多取多少条处理
    private static final int MAX_BATCH = 500;
    //创建队列 blockingqueue:阻塞队列
    private final BlockingQueue<AccessLog> queue = new ArrayBlockingQueue<>(10000);
    //累计丢弃条数
    private final AtomicLong dropped = new AtomicLong();
    //后台线程停止开关
    private volatile boolean running = true;
    private Thread worker;

    public void submit(AccessLog accessLog){
        //将访问日志加入阻塞队列，如果加入失败就记录失败的数量
        if(!queue.offer(accessLog)){
            //incrementAndGet:递增并且获取记录失败的日志数量
            long total = dropped.incrementAndGet();
            //记录失败的数量达到一万条的时候打一条日志
            if (total%10000==1){
                log.warn("访问日志队列已满，累计丢弃{}条",total);
            }
        }
    }
    //在spring启动的时候将这个bean创建完之后自动调用一次
    @PostConstruct
    public void start() {
        //创建一个新的线程worker,名字叫做access-log-worker,调用loop()方法
        worker = new Thread(() -> loop(), "access-log-worker");
        worker.setDaemon(true);
        worker.start();
        log.info("访问日志采集线程已启动，队列容量 {}", QUEUE_CAPACITY);
    }
    private void loop() {
        while (running) {
            try {
                // 如果队列里面有日志就拿走处理
                // 队列空的时候，take() 会「停在这里等」，不浪费 CPU
                AccessLog first = queue.take();
                List<AccessLog> batch = new ArrayList<>();
                batch.add(first);
                // 把队列里剩下的尽量都拿走，最多再拿 MAX_BATCH-1 条；没有就直接返回
                queue.drainTo(batch, MAX_BATCH - 1);

                handle(batch);
            } catch (InterruptedException e) {
                // 停机时被 interrupt 打断，正常退出
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("处理访问日志批次失败", e);
            }
        }
    }
    private void handle(List<AccessLog> batch) {
        try {
            int rows = accessLogMapper.insertBatch(batch);
            log.info("写入 {} 条访问日志，队列还剩 {} 条", rows, queue.size());
        } catch (Exception e) {
            log.error("批量写入访问日志失败，本批 {} 条已丢弃", batch.size(), e);
        }
        countInRedis(batch);
    }
    private void countInRedis(List<AccessLog> batch) {
        // 第一步：分组。500 条日志可能只涉及 10 个码，分组后只需要 10 次 Redis 调用
        Map<String, List<AccessLog>> grouped = new HashMap<>();
        //增强for循环，每次循环遍历batch，用accessLog来拿
        for (AccessLog accessLog : batch) {
            //获取日志的时间，设置格式
            String day = accessLog.getAccessTime().format(DAY_FORMAT);
            //获取日志的分组名
            String groupKey = accessLog.getCode() + "|" + day;
            //根据分组名查询分组，如果有就返回分组，如果没有就新建一个分组并且存入map，然后调用add加入日志
            grouped.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(accessLog);
        }

        // 第二步：每组分别累加
        //用一个键值对(Entry),名字叫group,将grouped的这个键值对拿出来
        //grouped键值对应该是这样的：V0001|20261026：[accessLog1,accessLog2,...]
        //这样是从Map中所有分组中拿一个分组
        for (Map.Entry<String, List<AccessLog>> group : grouped.entrySet()) {
            //从这个分组中获取值(一个集合：里面全都是accessLog)，用list来接收
            List<AccessLog> list = group.getValue();
            //从集合里面拿一条，这里拿第一条，这个集合里面的所有code和day肯定是相同的
            AccessLog first = list.get(0);
            //获取code
            String code = first.getCode();
            //获取日期，并设置格式
            String day = first.getAccessTime().format(DAY_FORMAT);
            //构建redis里的pv和uv，pv:一共点了多少次，uv:有几个人点了
            //pvKey="pv:"+"V0001"+":"+"20261026"
            //uvKey同理
            String pvKey = PV_PREFIX + code + ":" + day;
            String uvKey = UV_PREFIX + code + ":" + day;

            try {
                // PV：这一组有几条，就加几次
                //increment(要操作的字符串的值（pvKey）,将这个值加n（list.size()）)
                redisTemplate.opsForValue().increment(pvKey, list.size());

                // UV：把这一组的 IP 交给 HyperLogLog 去重
                //将这一组的ip放进List集合中
                List<String> ips = new ArrayList<>();
                //遍历list，用item来拿，这里每次拿到的都是一个accessLog
                for (AccessLog item : list) {
                    //如果能拿到ip且不为null，就将他放进List集合里面
                    if (item.getIp() != null) {
                        ips.add(item.getIp());
                    }
                }
                //如果集合里面有数据(非空)，就进行uv累加
                if (!ips.isEmpty()) {
                    redisTemplate.opsForHyperLogLog().add(uvKey, ips.toArray(new String[0]));
                }

                // 35 天后自动删除，避免 key 无限增长
                redisTemplate.expire(pvKey, STATS_TTL);
                redisTemplate.expire(uvKey, STATS_TTL);
            } catch (Exception ex) {
                // Redis 出问题只影响统计，绝不能影响用户跳转
                log.warn("写统计失败 code={} day={}", code, day, ex);
            }
        }
    }

    /** Spring 销毁这个 Bean 之前调用：停线程 + 把剩下的刷一遍 */
    @PreDestroy
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
        log.info("采集线程已停止，累计丢弃 {} 条", dropped.get());
    }

}



