# ============================================================
# MindMan 前端镜像 —— 多阶段构建
#   阶段 1：Node 构建出静态文件 dist/
#   阶段 2：Nginx 托管静态文件，并把 /api 反向代理到后端容器
# ============================================================

# ---------- 阶段 1：构建 ----------
FROM node:22-alpine AS build
WORKDIR /build

# 先只复制依赖清单，利用层缓存（依赖没变时不必重装）
COPY package.json package-lock.json ./
RUN npm ci --registry=https://registry.npmmirror.com

# 复制源码与 .env（其中 VITE_API_MODE=real，决定构建产物调用真实后端接口）
COPY . .
RUN npm run build

# ---------- 阶段 2：运行 ----------
FROM nginx:1.27-alpine

# 替换默认站点配置（含 SPA 路由回退与 /api 反向代理）
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80
