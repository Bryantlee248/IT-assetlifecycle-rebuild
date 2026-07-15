# 前端多阶段构建：
#   阶段1 用 node:22-alpine 安装依赖并构建 dist（vue-tsc + vite build）
#   阶段2 用 nginx:1.27-alpine 提供静态资源并反向代理 /api 到 backend
#
# 注意：VITE_API_BASE 通过 ENV 在【构建期】注入（.env 文件已被 .dockerignore 排除），
#       不依赖镜像内的 .env 文件。构建期 bake 进 bundle。

# ---------- 阶段 1：构建 ----------
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
ENV VITE_API_BASE=/api
RUN npm run build

# ---------- 阶段 2：运行 ----------
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
