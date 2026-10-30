# ============================================================
# 前端镜像：两阶段构建
#   阶段 1 用 node 把 Vue 打成静态文件
#   阶段 2 用 nginx 托管静态文件，同时把 /api、/uploads、短码跳转代理给后端
# 构建：docker build -t shortlink-web .
# ============================================================

# ---------- 阶段 1：构建 ----------
FROM node:20-alpine AS build

WORKDIR /build

COPY package*.json ./
# 有 lock 文件就用 npm ci（严格按 lock 装，可复现）
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# ---------- 阶段 2：托管 ----------
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
