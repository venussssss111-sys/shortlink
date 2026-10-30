do# 在 Windows 11 上装 Docker Desktop

> 你需要它吗？
> **不是必须的。** 你本机 3306 已经有一个 MySQL 8.0.34 在跑，v1 用那个就够。
> 但装一次之后，MySQL / Redis / 以后的部署环境都不用再操心，**第 2 周的 Redis 在 Windows 上也没有更好的选择**，所以长期看是值得的。

预计耗时：下载 10 分钟 + 安装 5 分钟 + 一次重启。

---

## 一、装之前先确认三件事

### 1. 虚拟化有没有开

任务管理器（Ctrl+Shift+Esc）→ 性能 → CPU → 看右下角 **虚拟化**：

- 显示「已启用」→ 继续
- 显示「已禁用」→ 重启进 BIOS（开机狂按 F2 / Del / F10），找 `Intel VT-x` 或 `AMD-V` 或 `SVM Mode`，设为 Enabled

### 2. WSL2 装没装

管理员身份打开 PowerShell（开始菜单搜 PowerShell → 右键 → 以管理员身份运行），执行：

```powershell
wsl --status
wsl -l -v
```

- 如果提示没有 WSL 或者没有发行版，执行：
  ```powershell
  wsl --install
  wsl --update
  ```
  然后**重启电脑**。
- `wsl -l -v` 里能看到 `VERSION 2` 就对了。如果显示 1：
  ```powershell
  wsl --set-default-version 2
  ```

### 3. 你是不是管理员

装 Docker Desktop 必须用**管理员权限**运行安装包。你自己的笔记本一般就是管理员账号。

---

## 二、下载并安装

官方下载页：<https://www.docker.com/products/docker-desktop/>

Windows 版直链（AMD64）：

```
https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe
```

双击安装，两个勾都保持默认（`Use WSL 2 instead of Hyper-V` 要勾上），一路 Next，装完**重启**。

重启后 Docker Desktop 会自动启动，右下角托盘出现小鲸鱼图标，等它变成绿色 / `Engine running`。

---

## 三、验证

打开 PowerShell：

```powershell
docker version
docker compose version
docker run --rm hello-world
```

三条都不报错，就装好了。

> 第一次 `docker run` 会拉镜像，国内可能很慢或超时 —— 见下面「四、配镜像加速」。

---

## 四、配国内镜像加速（重要）

Docker Desktop 右上角齿轮 → **Docker Engine**，把 JSON 改成：

```json
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://docker.1ms.run"
  ]
}
```

点 **Apply & restart**。

> 镜像站经常失效，如果拉不动就去搜「docker 镜像加速 2026」换一个能用的地址。

---

## 五、跑起来

在项目根目录（`D:\dafeiyuwork\shortlink`）：

```bash
docker compose up -d          # 起 MySQL(3307) + Redis(6379)
docker compose ps             # 看两个容器是不是 Up
docker compose logs -f mysql  # 看 MySQL 日志，出现 "ready for connections" 就好了
```

MySQL 在 `localhost:3307`，root 密码 `123456`，库 `shortlink` 会自动建好（`sql/01-schema.sql`、`sql/02-user.sql` 按文件名顺序执行）。

然后启动后端：

```bash
mvn spring-boot:run
```

常用命令：

```bash
docker compose stop      # 停
docker compose start     # 起
docker compose down      # 删除容器（数据还在 volume 里）
docker compose down -v   # 连数据一起删，慎用
```

---

## 六、常见报错

| 报错 | 原因 | 解决 |
|---|---|---|
| `WSL 2 installation is incomplete` | WSL 没装或版本太老 | `wsl --update`，重启 |
| `Hardware assisted virtualization... not enabled` | BIOS 里虚拟化没开 | 进 BIOS 开 VT-x / AMD-V |
| `Port 3307 is already in use` | 端口被占 | `netstat -ano \| findstr 3307` 找到进程杀掉，或改 [docker-compose.yml](../docker-compose.yml) 的端口 |
| 拉镜像卡住 / `TLS handshake timeout` | 没配镜像加速 | 见第四节 |
| Docker Desktop 起不来，一直 Starting | WSL 内核需要更新 | `wsl --update`，或卸载重装 Docker Desktop |
| `permission denied ... docker.sock` | 当前用户不在 docker-users 组 | 注销重新登录一次（Windows 组权限要重新登录才生效） |

---

## 七、装完记得

Docker Desktop 开机自启会占内存（约 1–2 GB）。如果你只是写代码不跑容器，可以在
Settings → General 里关掉 `Start Docker Desktop when you sign in`，用的时候再开。
