# =====================================================================
# 多阶段构建：Vite 打包 → Nginx 托管
# =====================================================================

# ---- 阶段 1: 构建 ----
FROM node:18-alpine AS build

WORKDIR /app

# 先拷 package.json 预热依赖
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# 拷源码 + 构建
COPY . .
RUN npm run build && ls -lh dist/

# ---- 阶段 2: Nginx 托管 ----
FROM nginx:alpine

# 自定义 Nginx 配置（前端静态 + 反向代理后端 API）
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 拷打包产物
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
