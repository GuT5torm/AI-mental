# ---- 构建阶段 ----
FROM node:20-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# 上传文件访问基址（构建期注入）。留空 = 同源相对路径 /files/...，由 nginx 反代到后端；
# 分离域名部署时在 compose 的 build.args 传入 https://files.example.com 之类完整地址
ARG VITE_FILE_BASE_URL=""
ENV VITE_FILE_BASE_URL=$VITE_FILE_BASE_URL
RUN npm run build

# ---- 运行阶段 ----
FROM nginx:1.27-alpine
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80
