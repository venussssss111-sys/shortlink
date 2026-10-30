# 压测（第 2 周）

> 这一周唯一的产出是**一张对比表**：加缓存前 vs 加缓存后。
> 没有基线，后面所有「优化了 XX%」都是空话。

## 文件说明

| 文件 | 作用 |
|---|---|
| `redirect.jmx` | JMeter 测试计划：并发请求 `GET /{code}`，不跟随重定向 |
| `seed-data.sh` | 造 200 条测试短链（`lt0001`~`lt0200`），并生成 `codes.csv` |
| `clear-data.sh` | 删掉这 200 条测试数据 |
| `codes.csv` | 由 `seed-data.sh` 生成，JMeter 从这里读短码 |

## 四步跑起来

### 1. 起依赖

```bash
# 确认 Docker Desktop 已经启动（任务栏小鲸鱼是绿的）
cd shortlink
docker compose up -d
docker compose ps          # 等 mysql 变成 healthy
```

### 2. 用压测 profile 启动应用

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=loadtest
```

**为什么要加 profile**：默认配置会打印每条 SQL、开 debug 日志。压测时每秒几千请求，日志 IO 会把 CPU 吃满，你测到的是「控制台打印速度」，不是「接口速度」。`application-loadtest.yml` 把这些都关了。

### 3. 造数据

```bash
bash loadtest/seed-data.sh
```

看到 `新建 200 条，已存在 0 条，失败 0 条` 就对了。

### 4. 跑压测

```bash
cd loadtest
jmeter -n -t redirect.jmx -l result.jtl -e -o report
```

跑完打开 `loadtest/report/index.html`，里面就是吞吐量、响应时间分位、错误率。

## 参数怎么调（不用改文件）

```bash
# 200 并发、压 2 分钟
jmeter -n -t redirect.jmx -Jthreads=200 -Jduration=120 -l result2.jtl -e -o report2

# 换成测「不存在的短码」（第 2 周验证布隆过滤器用）
jmeter -n -t redirect.jmx -Jcsv=codes-invalid.csv -l result404.jtl -e -o report404
```

可覆盖的参数：`host`、`port`、`threads`、`ramp`、`duration`、`csv`。

## 要记录的数字（四个就够）

| 指标 | 在哪看 | 为什么要它 |
|---|---|---|
| Throughput（QPS） | dashboard 顶部 | 系统每秒能扛多少请求 |
| p99 响应时间 | Statistics 表 | 最慢的那 1% 用户等了多久 |
| Error % | dashboard 顶部 | 有没有开始报错 |
| 平均响应时间 | Statistics 表 | 和 p99 一起看，判断有没有长尾 |

## 基线记录表

> 完整版（口径、作废数据、面试说法、复现命令）见 [`docs/压测报告.md`](../docs/压测报告.md)。本表是它的摘要。
> QPS 口径：样本数 ÷ 实测时长（59.8s）。均出自 `python summarize_all.py`。

| 版本 | 并发 | QPS | p99(ms) | 平均(ms) | 错误率 | 备注 |
|---|---|---|---|---|---|---|
| v1（无缓存） | 100 | 7,088 | 37 | 12.9 | 0.00 | 42.4 万请求；1 请求 = 1 次查库 |
| **v2（Redis 缓存）** | **100** | **17,701** | **10** | **5.1** | **0.00** | 105.8 万请求；100 万请求只查 **1** 次库 |
| v2（Redis 缓存） | 200 | 17,180 | 18 | 10.0 | 0.85\* | \*错误全是压测机自己 `BindException`，服务端 0 错 |
| v2（缓存**强制不命中**，对照） | 100 | 6,713 | 56 | 13.6 | 100%\*\* | \*\*打的是不存在的码，本来就该 404 |
| v2.1（空值缓存 + 随机 TTL，真实码） | 100 | 17,728 | 9 | 5.1 | 0.00 | 与 v2 持平 |
| **v2.1（空值缓存，不存在的码）** | **100** | **18,781** | **10** | **4.8** | 100%\*\* | 112.3 万请求；**6,713 → 18,781（2.8×）** |
| v2.1（不存在的码，**未冷却**） | 100 | 12,835 | 68 | 7.1 | — | 2.7 万条 `BindException`，**作废** |
| Redis 版布隆（Redisson） | 100 | 5,743 | 43 | 16.0 | 0.00 | 每请求 11 条 Redis 命令，**负收益** |
| **v3（Guava 布隆，真实码）** | **100** | **11,951** | **49** | **7.6** | **0.00** | 本轮机器整体变慢，见下 |
| **v3（Guava 布隆，不存在的码）** | **100** | **16,693** | **98** | **5.5** | 100%\*\* | **Redis 命令 ≈ 0 条/请求** |
| v6 容器化（nginx 反代，**未开 upstream keepalive**） | 500 | 6,237 | 234 | 73.8 | 0.00 | 37.2 万请求；nginx 吃掉 11.6 核，后端容器 1.4 万 TIME_WAIT |
| **v6 容器化（nginx 反代，开 keepalive）** | **500** | **15,688** | **62** | **28.0** | **0.00** | 62.2 万请求；**2.5× 吞吐，p99 从 234 降到 62** |

**为什么要跑对照行**：第 1、2 行跨了两次启动、两个进程，别人可以质疑「你 v1 是不是配置不一样」。
第 4、6 行是**同一份代码、同一批参数**，唯一变量是「缓存里有没有这个码」—— 6,713 → 18,781。

**v3 两行为什么偏低**：同一份缓存代码，10-23 跑 17,728，10-24 同参数重跑 4 次得到
16,898 / 14,561 / 14,331 / 11,882。机器被前面几轮压累了。**跨轮数字不可比，只认同轮内对照**——
v3 同一轮里：命中 11,951 < 不命中 16,693，因为布隆路径连 Redis 都不去。

## 结论（面试直接背）

- **QPS 7,088 → 17,701（2.5 倍）**，p99 37ms → 10ms，错误率 0。
- 更硬的指标不是 QPS，是**数据库查询次数**：100 万次跳转只产生 **1 次** SELECT。
  这个数字换任何机器都成立，而 QPS 会随机器变。
- 并发加到 200，QPS 17,180、冒出 0.85% 报错。逐条看，全是
  `java.net.BindException: Address already in use`——**压测机自己把本地端口用光了**，
  服务端 1,017,934 条响应全是 302。
  → 说明瓶颈已从数据库转到压测机和 Tomcat，这正是加缓存想要的结果。

## v2.1 的结论

- **缓存穿透被堵住了**：不存在的码 6,713 → **18,781 QPS（2.8 倍）**，p99 56ms → 10ms。
  和「真实码命中」几乎同一水平（17,728）——**拿随机码刷接口，效果和正常流量一样**，打不动数据库。
- 关键不是 QPS 变高，是**攻击面从「打穿到 DB」变成「打在 Redis 上」**。
- 顺带验证了坑：两轮之间不留冷却，第二轮会被 `TIME_WAIT` 拖到 12,835（2.7 万条 `BindException`）。
  **不是服务端问题**，冷却 150 秒后同参数重跑就是 18,781。

## v3 的结论（布隆过滤器 · 本地 Guava 版）

先说一个**负收益的坑**：Redis 版布隆（Redisson `RBloomFilter`）每次 `contains` 要发
1 次 `EXISTS` + 1 段 Lua + 7 次 `GETBIT`（布隆有 7 个哈希函数），实测**每请求 11 条 Redis 命令**，
真实码 QPS 17,728 → **5,743**。它是为了省一次查库，结果把 Redis 调用翻了 5 倍。
换成 Guava 本地内存版之后：

- 布隆路径**比缓存命中路径还快**（16,693 > 11,951），因为它连 Redis 都不去。
- 最硬的指标不是 QPS，是 **Redis 命令数**：100 次不存在的码，`1,101 → 1`；MySQL 查询 `0`。
- 100 万次不存在的码：Redis 一共 **76 条**命令，MySQL **0** 次查询。
- 假数字提醒：第一次跑只有 5,883，`TIME_WAIT` 15,613 条，p50 死卡 16ms（100÷16ms≈6,250）。
  **是压测机端口被占干**，冷却后同参数 → 16,693。
- 分层：布隆 1% 误判放行的请求由**空值缓存**兜住，两层都要留。

## 复现命令

```bash
cd loadtest
docker exec shortlink-redis redis-cli flushdb     # 清缓存，保证从冷启动开始
bash clear-data.sh && bash seed-data.sh           # 200 条测试数据

JAVA_HOME=/g/Android/jdk/jdk-17.0.2 \
  java -jar /g/develop/apache-jmeter-5.6.3/bin/ApacheJMeter.jar \
  -n -t redirect.jmx -Jport=8080 -Jthreads=100 -Jramp=10 -Jduration=60 \
  -l result-v2.jtl -j jm-v2.log

python percentile.py result-v2.jtl                # 算 p50/p90/p95/p99
```

## 造「不存在的短码」

```bash
cd loadtest
printf 'zz%04d\n' $(seq 1 200) > codes-invalid.csv
```

这些码库里不存在，用来验证：**加了布隆过滤器之后，这些请求应该一个都打不到数据库**（看 SQL 日志 / `SHOW GLOBAL STATUS LIKE 'Com_select'` 的增量）。

## 清理

```bash
bash loadtest/clear-data.sh
```

## 四个坑（大概率会踩）

1. **压测机和被测机是同一台** —— 只要 CPU 没跑满，结论就还站得住；一旦 CPU 打满，数字就没意义了。压测时用任务管理器盯着 Java 进程的 CPU。
2. **请求被重定向** —— `redirect.jmx` 里已经把 `follow_redirects` 关了。如果你自己新建测试计划，记得也关掉，否则测的是「跳过去那台外网服务器」的速度。
3. **`result.jtl` 已存在** —— JMeter 会拒绝覆盖报告目录，删掉 `report/` 再跑。
4. **两轮压测之间没留冷却时间** —— 上一轮几十万请求留下上万条 `TIME_WAIT`，
   下一轮会拿不到本地端口，报 `java.net.BindException: Address already in use`，
   吞吐直接腰斩（18,781 → 12,835）。**跑之前先 `netstat -an | grep -c TIME_WAIT`，
   等它降到几百再开始**，或者干脆隔 90 秒。
