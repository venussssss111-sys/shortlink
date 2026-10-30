# Docker Desktop 中英对照

> 你的 Docker Desktop 是 **29.9.2**，`frontend\locales` 里只有 `en-US.pak`，设置里也没有语言项 —— 这个版本没有中文界面资源。
> 下面这些词覆盖了 95% 的使用场景。实际开发你 99% 的时间在命令行，界面只是用来看"容器起没起来"。

## 一、左侧导航

| 英文 | 中文 | 干什么的 |
|---|---|---|
| Containers | 容器 | 看正在跑 / 已停止的容器，点进去能看日志、进终端 |
| Images | 镜像 | 本地有哪些镜像，能删、能 run |
| Volumes | 数据卷 | 容器删了数据还在的地方（MySQL 数据就存在这） |
| Builds | 构建 | 用 Dockerfile 构建的历史记录 |
| Kubernetes | K8s | 本机单节点集群，这个项目用不到，建议关掉省内存 |
| Extensions | 扩展 | 插件市场，用不到 |
| Settings | 设置 | 资源、镜像加速、自动更新都在这 |
| Troubleshoot | 故障排查 | 重启、清数据、恢复出厂 |

## 二、Containers 页面

| 英文 | 中文 |
|---|---|
| Name | 容器名（我们的是 `shortlink-mysql` / `shortlink-redis`） |
| Image | 用的哪个镜像 |
| Status | 状态，`Running` = 跑着，`Exited` = 停了 |
| Port(s) | 端口映射，`0.0.0.0:3307->3306/tcp` 表示宿主机 3307 转到容器 3306 |
| CPU / Memory | 实时占用 |
| Started | 启动时间 |

容器右侧按钮：

| 英文 | 中文 |
|---|---|
| Start / Stop / Restart | 启动 / 停止 / 重启 |
| Delete | 删除（数据卷默认保留） |
| Open in terminal | 打开终端（进容器里） |
| Logs | 日志，排查起不来必看 |
| Inspect | 看完整配置 JSON |

## 三、Settings 里常用的几项

| 路径 | 英文 | 中文 |
|---|---|---|
| General | Start Docker Desktop when you sign in | 开机自启（吃内存，建议关） |
| General | Theme | 主题（你现在是 dark） |
| Resources → Advanced | CPUs / Memory / Swap | 分配多少 CPU 和内存给 Docker |
| Resources → Advanced | Disk image size | 虚拟磁盘上限，镜像多了要调大 |
| Resources → File sharing | 共享目录 | 默认 C 盘，项目在 D 盘一般不用改 |
| Docker Engine | JSON 配置 | **国内镜像加速配这里** |
| Software updates | Automatically check for updates | 自动更新（装了汉化包必须关掉） |

## 四、命令行（这个才是你天天用的）

```bash
# 起 / 停本项目
docker compose up -d          # 后台启动
docker compose ps             # 看状态
docker compose logs -f mysql  # 跟踪 MySQL 日志
docker compose stop           # 停止（容器还在）
docker compose start          # 再启动
docker compose down           # 删除容器（数据卷保留）
docker compose down -v        # 连数据卷一起删（慎用）

# 通用
docker ps                     # 跑着的容器
docker ps -a                  # 包括已停止的
docker images                 # 本地镜像
docker logs <容器名> -f       # 看日志
docker exec -it <容器名> bash # 进容器
docker system df              # 磁盘占用
docker system prune           # 清理无用数据（别加 -a --volumes，会删数据）
```

## 五、这个项目的实际端口

| 服务 | 宿主机端口 | 容器内端口 | 账号 |
|---|---|---|---|
| MySQL | 3307 | 3306 | root / 123456 |
| Redis | 6379 | 6379 | 无密码 |
| 你的应用 | 8080 | — | 跑在宿主机上，不在容器里 |

> 你本机 3306 已经被另一个 MySQL 服务占着（`G:\BaiduNetdiskDownload\mysql-8.0.34-winx64`），
> 所以 compose 里特意映射成 3307，两边不打架。
