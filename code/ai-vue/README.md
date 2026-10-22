# 心理健康AI助手（前端）

基于 Vue 3 + Vite 的心理咨询与情绪陪伴 Web 应用前端。本仓库仅包含前端代码，需配合独立的后端服务运行。

## 项目简介

心理健康AI助手面向有情绪陪伴与心理疏导需求的一般用户，提供 AI 咨询对话、情绪日记、心理知识阅读等功能；同时为运营人员提供后台管理，涵盖数据分析、知识文章管理、咨询记录与情绪日志管理。

## 功能特性

### 用户端

- 首页：品牌展示与入口引导
- AI 咨询：发起咨询会话、查看历史会话、查看单次会话的情绪分析结果
- 情绪日记：记录每日情绪
- 心理知识库：文章分类浏览、文章列表与详情阅读
- 账户：注册、登录、退出登录

### 管理后台（`/back`）

- 数据分析：概览指标与图表展示
- 知识文章：文章增删改查、封面上传、状态切换、分类树管理
- 咨询记录：会话列表与消息详情查看
- 情绪日志：用户情绪日记的查看与删除

### 权限模型

路由守卫基于 `localStorage` 中的 `userInfo.userType` 进行分流：

- `userType = 1`：普通用户，仅可访问用户端页面
- `userType = 2`：管理员，访问后台页面，否则被重定向至登录页

## 技术栈

- Vue 3（`<script setup>` 单文件组件）
- Vite 8（构建与开发服务器）
- Element Plus 2（UI 组件库）
- Pinia 4（状态管理）
- Vue Router 4（含路由守卫）
- Axios（请求封装，统一拦截器处理 token 与业务状态码）
- ECharts 6（后台图表）
- @wangeditor/editor（文章富文本编辑）
- @microsoft/fetch-event-source（会话流式响应）
- Sass（样式）

## 目录结构

```
src/
├── api/            # 接口封装（frontend 用户端 / gutts 后台）
├── assets/         # 静态资源
├── components/     # 公共组件（布局、导航、富文本编辑器等）
├── config/         # 运行配置（文件访问基址等）
├── router/         # 路由与守卫
├── stores/         # Pinia 状态
├── utils/          # 请求封装等工具
├── views/          # 页面
├── App.vue
└── main.js
```

## 本地开发

### 环境要求

- Node.js（Vite 8 构建依赖，建议使用 Node 20 及以上）
- 包管理器：npm

### 安装依赖

```bash
npm install
```

### 启动开发服务器

```bash
npm run dev
```

默认地址为 http://localhost:5173 。

### 构建与预览

```bash
npm run build     # 产物输出至 dist/
npm run preview   # 本地预览构建产物
```

## 后端接口与代理

前端通过 `/api` 前缀访问后端，开发环境下由 Vite 代理转发：

- `vite.config.js` 中 `/api` 代理至 `http://localhost:1236`
- `src/config/index.js` 中的 `fileBaseUrl` 为上传文件访问基址，同样指向后端端口 `http://localhost:1236`

启动前端前需确保后端服务已在 `localhost:1236` 运行，否则接口请求与文件访问将失败。切换部署环境时，需同步修改 `vite.config.js` 的代理目标与 `src/config/index.js` 的文件基址。

## 说明

本仓库为前端代码仓库，不包含后端实现、模型与训练相关代码。
