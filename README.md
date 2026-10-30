# ShortLink · 短链接服务

Java 全栈短链接服务：长链转短链、302 跳转、访问统计、鉴权限流、管理后台。

**一条命令跑起来**：`docker compose up -d --build` → <http://localhost:8088>

- 500 并发压测 **15,688 QPS**，P99 62 ms（数据见 [docs/压测报告.md](docs/压测报告.md)）
- 防穿透三件套：热点读缓存 + 本地布隆过滤器 + 空值缓存（随机 TTL）
- 安全：手写 JWT、Redis 令牌桶限流（Lua 原子）、多租户越权隔离

| 层 | 技术 |
|---|---|
| 后端 | JDK 17 · Spring Boot 3.3 · MyBatis-Plus · MySQL 8 · Redis 7 |
| 前端 | Vue 3 · Vite · Element Plus · Axios（ECharts 留给统计页） |
| 部署 | Docker Compose（nginx + Spring Boot + MySQL + Redis，一条命令起全套） |
| 压测 | JMeter |

## 目录结构

```
shortlink/
├── Dockerfile               # 后端两阶段构建（maven 编译 → JRE 运行）
├── docker-compose.yml       # 四个服务：mysql / redis / backend / frontend
├── docs/
│   ├── 需求文档.md          # 这个项目做什么、为什么这么做
│   ├── 路线图.md            # 六周可打勾计划
│   ├── 部署.md              # 部署架构、nginx 规则、常见问题
│   └── openapi.yaml         # 前后端接口契约（给 AI 生成前端用）
├── sql/
│   ├── 01-schema.sql        # 建库建表（short_link / access_log / link_stats）
│   ├── 02-user.sql          # 用户表 + 默认管理员
│   └── migrations/          # 老库增量迁移（新装不用管）
├── src/main/java/com/dafeiyu/shortlink/
│   ├── ShortLinkApplication.java
│   ├── common/              # 统一返回、异常、全局处理、UserContext
│   ├── config/              # MyBatis-Plus、跨域、JWT、鉴权/限流拦截器
│   ├── controller/          # 接口
│   ├── dto/                 # 入参/出参
│   ├── entity/              # 数据库实体
│   ├── mapper/              # 数据访问
│   ├── service/             # 业务逻辑（含采集器、聚合、限流、布隆）
│   └── util/                # Base62、URL 校验、JWT
├── src/main/resources/
│   ├── application.yml
│   └── lua/token_bucket.lua # 令牌桶脚本
├── frontend/                # Vue 3 管理台（Vite + Element Plus）
│   ├── Dockerfile           # 前端两阶段构建（node 打包 → nginx 托管）
│   ├── nginx.conf           # 静态托管 + /api、/uploads、短码跳转反代
│   ├── src/api/             # 接口封装，统一拆 {code,message,data}
│   ├── src/views/           # 列表页、统计页、登录页
│   └── src/components/      # 弹窗、图表、用户菜单
├── loadtest/                # JMeter 压测计划 + 脚本
└── pom.xml
```

## 快速开始

### 0. 启动（Docker，推荐）

```bash
docker compose up -d --build
```

四个容器（mysql / redis / backend / frontend）都健康后，打开 <http://localhost:8088>。

建库、建表、默认账号全部由 compose 自动完成，**不需要手动执行任何 SQL**。
首次构建要拉镜像 + 下载 Maven 依赖，10~45 分钟取决于网络；之后改代码重建 1~2 分钟。

### 1. 试一下

```bash
TOKEN=$(curl -s -X POST http://localhost:8088/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

curl -X POST http://localhost:8088/api/links \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"originUrl":"https://www.baidu.com/s?wd=shortlink"}'
```

返回里的 `shortUrl` 就是新短链，浏览器打开会 302 跳回原地址。

### 2. 本地开发（不用 Docker）

只起依赖，后端和前端在本地跑：

```bash
docker compose up -d mysql redis            # MySQL 3307 / Redis 6379
mvn spring-boot:run                         # 后端 8080
cd frontend && npm install && npm run dev   # 前端 5173
```

- 前端把 `/api` 通过 Vite proxy 转给 `localhost:8080`，**不用处理跨域**，但后端必须同时开着。
- 如果 3306 端口已被别的 MySQL 占用：把 [application.yml](src/main/resources/application.yml) 里的 `3307` 和密码改成自己的即可。
- `npm run dev` / `npm run build` 报 `Access is denied`（esbuild 删临时文件失败）时，把临时目录指到工作区：

  ```bash
  mkdir -p D:\dafeiyuwork\.tmp
  export TEMP='D:\dafeiyuwork\.tmp'
  ```

### 3. 登录

管理台需要登录才能用（默认账号 **admin / 123456**，**仅本机演示用**）。

> ⚠️ 只要打算放到公网，先去右上角头像 → 修改密码，把 `123456` 换掉。默认密码写在仓库里，不改等于把管理员权限公开。

鉴权链路（面试可以直接照着讲）：

| 环节 | 实现 | 为什么这么做 |
|---|---|---|
| 密码存储 | `MD5(salt + 明文)`，每个用户独立随机盐 | 同样密码不同用户 hash 不同，防彩虹表 |
| 登录凭证 | 用 `HmacSHA256` 手写的 JWT（`util/JwtUtil.java`） | 就是两段 base64 + 一次签名，看懂比调库值钱 |
| 校验 | `AuthInterceptor` 拦 `/api/**`，验签 + 验过期 | 只拦接口；`/{code}` 跳转必须能匿名访问 |
| 传递用户 | `ThreadLocal`（`common/UserContext.java`） | 拦截器 set、业务 get、`afterCompletion` remove |
| 退出 | token 的 `jti` 写进 Redis 黑名单，TTL = 剩余寿命 | JWT 无状态，不拉黑就退不干净 |
| 登录失败 | 用户不存在和密码错误返回**同一句话** | 防止撞库的人枚举用户名 |
| 头像 | 存 `./uploads/avatar/`，映射成 `/uploads/**` | 文件名由服务端生成，避免路径穿越和覆盖 |

接口：`POST /api/auth/register`、`/login`、`/logout`、`/password`、`/avatar`，`GET /api/auth/me`。

### 4. 限流与安全

| 风险 | 实现 | 关键点 |
|---|---|---|
| 接口被刷 | 令牌桶（Redis + Lua），四个桶 | 「读-算-写」必须在同一个 Lua 里，否则并发扣票会超卖 |
| 超限体验 | 429 + `Retry-After` / `X-RateLimit-Remaining` | 快速失败，不让请求排队（Tomcat 200 线程会被占满） |
| 刷建链接口 | `create` 桶（容量 10 / 每秒 0.5） | 建链是唯一写库的接口，单独收紧 |
| 密码爆破 | `auth` 桶（容量 5 / 每秒 0.2） | 按 IP 限；登录/注册不在鉴权排除之外 |
| 开放重定向 / SSRF | `util/UrlValidator.java` | 协议白名单、`user:pass@` 写法、内网/回环/CGNAT/云元数据地址、非 80/443 端口 |
| 短码枚举 | `service/MissGuard.java` | 每分钟 404 超 30 次 → 429；只对 404 计数，410（停用/过期）不算猜错 |

阈值都在 [application.yml](src/main/resources/application.yml) 的 `shortlink.rate-limit` 下。

## 配置（环境变量）

所有密码和密钥都从环境变量读，仓库里只保留「本地演示」用的默认值。

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3307/shortlink?...` | 本机开发指向容器里的 3307；compose 里改成 `mysql:3306` |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / `123456` | 数据库账号密码 |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | |
| `JWT_SECRET` | 演示串 | **上线必须换**：知道默认值就能伪造任意用户的 token |
| `JWT_EXPIRE_MINUTES` | `1440` | token 有效期（分钟） |
| `SHORTLINK_DOMAIN` | `http://localhost:8080` | 生成短链时拼在前面的域名 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:8088` | 逗号分隔；部署地址必须在内，否则浏览器登录会 403 |
| `UPLOAD_DIR` | `./uploads` | 头像落盘目录 |

Docker 部署时从项目根目录的 `.env` 读（模板见 [`.env.example`](.env.example)）：

```bash
cp .env.example .env     # 改 DB_PASSWORD 和 JWT_SECRET
docker compose up -d
```

`.env` 在 `.gitignore` 里，不会进仓库。什么都不配也能跑，用的是演示默认值。

改 `JWT_SECRET` 后所有人要重新登录（旧 token 验签失败）——这一点是实测过的：
容器换成新密钥后旧 token 立刻 401，换回来又恢复 200。

## 部署说明

四个容器都带 `healthcheck`，`docker compose ps` 应全部为 `healthy`。

架构图、nginx 为什么要把短码路由单独拎出来、镜像怎么从 700MB 瘦到 429MB、构建卡住怎么排查，见 [docs/部署.md](docs/部署.md)。

## 界面

登录页（JWT 鉴权，左上角是品牌标，强调色只出现在主按钮）：

![登录](docs/screenshots/login.png)

访问统计页（Linear 设计语言：深色底 + 近黑面板 + 细描边 + 单一强调色，强调色只用在品牌标、主按钮和数据上）；含 PV/UV 趋势、Top 榜、来源饼图、设备柱状图：

![访问统计](docs/screenshots/stats.png)

短链管理页：

![短链管理](docs/screenshots/links.png)

> 页面的视觉规范写在 `frontend/src/styles.css` 顶部的设计变量里，改配色只改那一段。

## 当前进度

- [x] v1 后端骨架：生成 / 跳转 / 列表 / 停用 / 删除
- [x] v2-1 Redis 缓存（QPS 7,088 → 17,701，p99 37 → 10 ms）
- [x] v2-2 防穿透：本地布隆过滤器 + 空值缓存（随机 TTL），随机不存在短码 6,713 → 16,693 QPS
- [x] v3 访问统计：有界队列采集 → `access_log` → Redis PV/UV → 每日聚合进 `link_stats` → 查询接口
- [x] v4 前端：短链管理 + 访问统计看板（趋势折线 / Top 榜 / 单码查询）
- [x] v4-2 来源饼图（referer 拆域名）、设备柱状图（UA 归类）、登录页
- [x] v5-1 登录鉴权：注册 / 登录 / 退出（Redis 黑名单）/ 改密码 / 上传头像
- [x] v5-2 令牌桶限流（Redis + Lua，四个桶）+ URL 黑名单 + 枚举防护
- [x] v6 Docker 部署：两阶段镜像 + nginx 单入口 + 健康检查，`docker compose up -d --build` 一条命令起全套
- [x] v6 压测：500 并发 15,688 QPS / p99 62ms；发现并修复 nginx upstream 未开 keepalive（6,237 → 15,688）
- [x] 文档：`docs/架构图.md`（架构/时序/ER，Mermaid）、`docs/简历-项目描述.md`（4 条 bullet + 12 个追问）
- [x] v7 权限：短链归属（`user_id`）+ 管理员 `is_admin`；列表/改/删/详情/统计全部按用户隔离，越权返回 404

## 文档地图

| 文档 | 讲什么 |
|---|---|
| [docs/需求文档.md](docs/需求文档.md) | 做什么、为什么这么做 |
| [docs/路线图.md](docs/路线图.md) | 六周的周计划与打勾进度 |
| [docs/部署.md](docs/部署.md) | 部署架构、nginx 规则、镜像瘦身、常见问题 |
| **[docs/压测报告.md](docs/压测报告.md)** | **压测数据全集**：四轮对比、作废数据、面试口径 |
| [docs/踩坑记录.md](docs/踩坑记录.md) | 踩过的坑与怎么爬出来的 |
| [docs/openapi.yaml](docs/openapi.yaml) | 接口契约（Apifox 可直接导入） |
| [loadtest/README.md](loadtest/README.md) | JMeter 怎么跑、基线记录表 |
| [docs/装Docker.md](docs/装Docker.md) | 本机装 Docker 的步骤 |
| [docs/Docker中英对照.md](docs/Docker中英对照.md) | Docker Desktop 界面中英对照 |

详细安排见 [docs/路线图.md](docs/路线图.md)。
