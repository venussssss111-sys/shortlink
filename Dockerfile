# ============================================================
# 后端镜像：两阶段构建
#   阶段 1 用 maven 镜像把源码打成 jar
#   阶段 2 只把 jar 放进 JRE 镜像，最终镜像里没有源码、没有 Maven、没有依赖缓存
# 构建：docker build -t shortlink-backend .
# ============================================================

# ---------- 阶段 1：构建 ----------
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

# 先只拷 pom：依赖没变时这一层能命中缓存，改代码不用重新下依赖
COPY pom.xml .
# -ntp = 不打印传输进度条，但每个依赖仍打一行，方便看构建卡在哪
RUN mvn -B -ntp dependency:resolve

COPY src ./src
RUN mvn -B -ntp package -DskipTests

# ---------- 阶段 2：运行 ----------
FROM eclipse-temurin:17-jre-alpine

# 用非 root 用户跑：容器被攻破时拿不到 root
RUN addgroup -S app && adduser -S app -G app

WORKDIR /app
COPY --from=build /build/target/*.jar /app/app.jar

# 头像落盘目录，宿主机挂卷进来
RUN mkdir -p /app/uploads && chown -R app:app /app
USER app

EXPOSE 8080

# 容器里的 JVM 要认 cgroup 的限制，不是宿主机内存
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -Dfile.encoding=UTF-8"

HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
    CMD wget -q -O- http://127.0.0.1:8080/api/health > /dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
