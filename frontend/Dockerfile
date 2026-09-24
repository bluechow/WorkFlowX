# syntax=docker/dockerfile:1

# ============================================================
# WorkFlowX Frontend — Node build → Nginx runtime（Phase 16; P16-05/06）
# ============================================================

# ---------- Build stage ----------
FROM node:20-alpine AS build
WORKDIR /build

# npmmirror 源加速（与开发机策略一致）
ENV NPM_CONFIG_REGISTRY=https://registry.npmmirror.com

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---------- Runtime stage ----------
FROM nginx:stable-alpine

# 移除默认配置，仅保留部署用 SPA + /api 反代
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/app.conf

COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=6 \
    CMD wget -qO- http://localhost/ >/dev/null || exit 1
