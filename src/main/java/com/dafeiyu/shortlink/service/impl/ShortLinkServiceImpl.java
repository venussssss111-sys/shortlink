package com.dafeiyu.shortlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dafeiyu.shortlink.bloom.BloomFilterService;
import com.dafeiyu.shortlink.common.BizException;
import com.dafeiyu.shortlink.common.UserContext;
import com.dafeiyu.shortlink.dto.CreateLinkRequest;
import com.dafeiyu.shortlink.dto.CreateLinkResponse;
import com.dafeiyu.shortlink.entity.ShortLink;
import com.dafeiyu.shortlink.mapper.ShortLinkMapper;
import com.dafeiyu.shortlink.service.ShortLinkService;
import com.dafeiyu.shortlink.util.Base62;
import com.dafeiyu.shortlink.util.UrlValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl implements ShortLinkService {

    /** 随机短码碰撞时的最大重试次数 */
    private static final int MAX_RETRY = 3;
    private static final String CACHE_KEY_PREFIX="link:";
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final ShortLinkMapper shortLinkMapper;

    private final BloomFilterService bloomFilterService;

    @Value("${shortlink.domain}")
    private String domain;

    @Value("${shortlink.code-length:6}")
    private int codeLength;

    @Override
    public CreateLinkResponse create(CreateLinkRequest request) {
        //校验链接：这个原始链接不能是空的，也不能超过2048个字符，协议只能是http或者是https。
        //如果不满足就抛出异常
        UrlValidator.validate(request.getOriginUrl());
        /*对于用户传进来的自定义短码进行校验
        * StringUtils.hasText是spring带的一个工具类
        * 如果里面的参数是null "" "   "就返回false */
        String customCode = StringUtils.hasText(request.getCustomCode())
                ? request.getCustomCode()
                : null;
        /*如果用户传进来了一个合法的自定义短码
        * 就对这个短码进行校验，这里主要判断的是短码是否已经被占用
        * 根据用户传进来的短码调用getFromDb方法从数据库中查
        * v2更改：本来是从getByCode中查找，改从getFromDb中查找
        * 因为getByCode方法目前更改为了先查缓存
        * 如果不为null则抛出异常*/
        if (customCode != null && getFromDb(customCode) != null) {
            throw BizException.conflict("短码 " + customCode + " 已被占用");
        }
        /*计算这个短码的过期时间，如果request.getExpireDays()==null成立
        * 则这个短码永久有效，否则这个短码的有效时间为系统时间到设置的有效时间*/
        LocalDateTime expireTime = request.getExpireDays() == null
                ? null
                : LocalDateTime.now().plusDays(request.getExpireDays());

        // 随机短码 + 数据库唯一索引兜底：插入冲突就换一个再试。
        // 第 2 周可以在这里优化成"先查布隆过滤器 / 先查 Redis"，减少一次 DB 往返。

        /*设置了一个常量，最多循环三次*/
        for (int i = 0; i < MAX_RETRY; i++) {
            //生成短码
            ShortLink link = new ShortLink();
            link.setCode(customCode != null ? customCode : Base62.random(codeLength));
            link.setOriginUrl(request.getOriginUrl().trim());
            link.setExpireTime(expireTime);
            link.setStatus(1);
            // 归属：谁创建的就算谁的（v7 之前没有这一列，历史数据由迁移脚本归给 admin）
            link.setUserId(UserContext.get());
            link.setCreateTime(LocalDateTime.now());
            link.setUpdateTime(LocalDateTime.now());
            try {
                shortLinkMapper.insert(link);
                deleteCache(link.getCode());
                // 新码必须立刻进布隆，否则刚创建就访问会 404
                bloomFilterService.add(link.getCode());
                log.info("create short link: {} -> {}", link.getCode(), link.getOriginUrl());
                return CreateLinkResponse.builder()
                        .code(link.getCode())
                        //domain的值是http://localhost:8080
                        .shortUrl(domain + "/" + link.getCode())
                        .originUrl(link.getOriginUrl())
                        .expireTime(link.getExpireTime())
                        .build();
            } catch (DuplicateKeyException e) {
                if (customCode != null) {
                    throw BizException.conflict("短码 " + customCode + " 已被占用");
                }
                log.warn("code collision, retry {}/{}", i + 1, MAX_RETRY);
            }
        }
        throw new BizException(500, "短码生成失败，请重试");
    }

    @Override
    public String resolve(String code) {
        ShortLink link = getByCode(code);
        if (link == null) {
            throw BizException.notFound("短链接不存在");
        }
        if (link.getStatus() != null && link.getStatus() == 0) {
            throw BizException.notFound("短链接已被停用");
        }
        if (link.getExpireTime() != null && link.getExpireTime().isBefore(LocalDateTime.now())) {
            throw BizException.gone("短链接已过期");
        }
        return link.getOriginUrl();
    }


    @Override
    public ShortLink getByCode(String code) {
        // 布隆过滤器先拦一道：它说「一定不存在」就直接返回，Redis 和 MySQL 都不用碰
        if (!bloomFilterService.mightExist(code)) {
            return null;
        }
        // 先看缓存：命中就直接返回，99.9% 的请求走这条路
        ShortLink cache = readCache(code);
        // 空值缓存：数据库确认过"这个码不存在"
        if (cache == NOT_EXIST) {
            return null;
        }
        if (cache != null) {
            return cache;
        }
        //为什么不在下面接着查数据库？
        //因为如果redis里面的热点key过期了
        //同一时间大量请求砸进来
        //那么这里加上一个查数据库，大量数据就会同时涌入数据库
        //这就是下面进行热点key互斥重建的原因
        // 缓存没命中 → 交给单飞重建：同一个码只放一个线程进数据库
        // ⚠️ 这里千万别顺手写"空值缓存"！
        //    空值缓存的含义是「库里也没有」，而此刻只是「缓存里没有」，库里很可能有。
        //    先写空值会导致：重建时双检读到 NOT_EXIST → 直接返回 null → 明明存在的链接 404。
        //    （这条路径曾经真的踩过，见 docs/踩坑记录.md）
        return rebuildWithSingleFlight(code);
    }
    /** 每个短码一把锁：缓存没命中时，只放一个线程去查库 */
    private final ConcurrentHashMap<String, Object> rebuildLocks = new ConcurrentHashMap<>();

    private ShortLink rebuildWithSingleFlight(String code){
        //为code创建一把锁，如果根据code查到了这个锁就直接返回
        //如果没有就创建一把锁
        //这里的k可以写成任意值，这是lambda表达式的要求，要求有一个占位符
        //这里会new出一个Object对象 lock，然后根据k传进来的code，生成一个new Object();
        //这个Object对象就是前面的lock,存进hashmap中
        Object lock = rebuildLocks.computeIfAbsent(code,k->new Object());
        try {
            //synchronized：只有获取到这个锁的线程才可以进来
            synchronized (lock){
                //进来第一步先读取缓存
                //为什么：一般来说第一次进来的线程读取缓存是一定读取不到的
                //所以下面查完之后会进行一次写缓存操作
                //锁释放之后下一个请求进来，如果没有读取缓存这一步操作
                //那么就会导致每次请求全部进入数据库然后在redis里面写一份数据
                //这样新写入的缓存就失效了
                ShortLink cache = readCache(code);
                //如果读取到缓存是空值直接返回null
                if (cache == NOT_EXIST) return null;
                //如果读取到了缓存且不为空就直接返回缓存里的数据
                if (cache != null) return cache;

                //进行查数据库
                ShortLink link = getFromDb(code);
                // 只有一个人会走到这里
                //如果查出来不为空就写入缓存
                if (link != null) writeCache(link);
                //如果查出来为空就写空值缓存
                else writeEmptyCache(code);
                return link;
            }
        } finally {
            //在操作最后删除这个code-lock映射，也就是在hashmap中没法根据code来找到这个lock了
            rebuildLocks.remove(code, lock);           // 用完把锁删掉
        }
    }

    @Override
    public IPage<ShortLink> page(int page, int size, String keyword) {
        LambdaQueryWrapper<ShortLink> wrapper = new LambdaQueryWrapper<>();
        // 多租户隔离：普通用户只看得见自己创建的；管理员看全部
        if (!UserContext.isAdmin()) {
            wrapper.eq(ShortLink::getUserId, UserContext.get());
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(ShortLink::getCode, keyword)
                    .or()
                    .like(ShortLink::getOriginUrl, keyword));
        }
        wrapper.orderByDesc(ShortLink::getId);
        return shortLinkMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 管理视角取短链：带归属校验。
     *
     * 不是自己建的，一律 404「短链接不存在」，而不是 403「无权限」。
     * 因为 403 等于告诉对方「这个码真实存在，只是不给你看」——
     * 别人拿 403 和 404 的差异，就能把全站短码枚举出来。
     */
    @Override
    public ShortLink getOwnedByCode(String code) {
        ShortLink link = getFromDb(code);
        if (link == null) {
            throw BizException.notFound("短链接不存在");
        }
        Long me = UserContext.get();
        Long owner = link.getUserId();
        if (!UserContext.isAdmin() && (owner == null || !owner.equals(me))) {
            throw BizException.notFound("短链接不存在");
        }
        return link;
    }

    @Override
    public void updateStatus(String code, int status) {
        //从数据库中查询短链接
        ShortLink link = getOwnedByCode(code);
        link.setStatus(status);
        link.setUpdateTime(LocalDateTime.now());
        shortLinkMapper.updateById(link);
        //更新短链接之后要将redis中之前的缓存也删掉
        deleteCache(code);
    }

    @Override
    public void delete(String code) {
        ShortLink link = getOwnedByCode(code);
        shortLinkMapper.deleteById(link.getId());
        deleteCache(code);
    }
    //写redis相关的逻辑，比如说新增缓存，删除缓存，查询缓存
    //只查询数据库，不走缓存
    private ShortLink getFromDb(String code){
        return shortLinkMapper.selectOne(
                //如果没查到会返回一个null
                new LambdaQueryWrapper<ShortLink>().eq(ShortLink::getCode,code)
        );
    }
    //查询缓存，不查数据库，如果缓存没有数据则返回null
    //定义一个对象，说明后端数据库中没有这个数据
    private static final ShortLink NOT_EXIST = new ShortLink();
    private ShortLink readCache(String code){
        //key 形如 link:lt0001
        String key = CACHE_KEY_PREFIX+code;
        String json;
        //如果redis连接不上，就当作这个缓存没查到
        try {
            json = redisTemplate.opsForValue().get(key);
        } catch (DataAccessException e) {
            log.warn("缓存读取失败，查询数据库：key = {}",key,e);
            return null;
        }
        //缓存未命中
        if(json==null){
            return null;
        }
        if(json.isEmpty()){
            return NOT_EXIST;
        }
        try {
            //为什么会出现缓存数据读不出来？
            //这个异常出现的原因是，这个json字符串无法被反序列化一个ShortLink对象
            //可能的原因就是项目中ShortLink的字段名发生改变了
            //例如OriginLink改成了Origin_Link
            //这就会导致无法返回数据
            return objectMapper.readValue(json,ShortLink.class);
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("缓存数据有误，正在删除缓存： key={}",key,e);
            redisTemplate.delete(key);
            return null;
        }
    }
    /*将一个数据放进redis中进行缓存*/
    private void writeCache(ShortLink link){
        //设置随机过期时间，避免短链接同时过期导致请求同时打数据库
        Duration ttl =Duration.ofSeconds(3600L + ThreadLocalRandom.current().nextLong(-360,361));
        try {
            redisTemplate.opsForValue().set(
                    CACHE_KEY_PREFIX + link.getCode(),
                    objectMapper.writeValueAsString(link),
                    ttl);
        } catch (JsonProcessingException | DataAccessException e) {
            log.warn("写缓存失败，忽略 code={}", link.getCode(), e);
        }
    }
    //根据code删除缓存
    private void deleteCache(String code){
        String key = CACHE_KEY_PREFIX+code;
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException e) {
            log.warn("缓存删除失败：key = {}",key,e);
        }
    }
    private static final Duration EMPTY_TTL = Duration.ofSeconds(45);
    private static final long EMPTY_JITTER_SECONDS = 15;
    private void writeEmptyCache(String code){
        Duration ttl = EMPTY_TTL.plusSeconds(ThreadLocalRandom.current().nextLong(-EMPTY_JITTER_SECONDS, EMPTY_JITTER_SECONDS + 1));
        try {
            redisTemplate.opsForValue().set(CACHE_KEY_PREFIX+code,"",ttl);
        } catch (DataAccessException e) {
            log.warn("写入空值缓存失败 code={}",code,e);

        }

    }
}
