# 心理健康 AI 助手

> 一个面向情绪陪伴与心理疏导场景的全栈 Web 应用：AI 流式对话、情绪日记、心理知识库与管理后台。

## 项目简介

本项目是一个个人全栈实践项目，围绕"情绪记录 + AI 陪伴 + 心理知识科普"构建，包含完整的前端、后端、数据库结构与自动化测试。

用户可以通过 AI 咨询获得即时陪伴，在情绪日记中记录每日状态并获取结构化分析；同时，管理员可以在后台查看运营数据、管理知识文章、查阅咨询记录与情绪日志。

## 界面预览

| 首页 | AI 咨询 |
| --- | --- |
| ![首页](docs/screenshots/01-home.png) | ![AI 咨询](docs/screenshots/04-consultations.png) |

| 情绪日记 | 数据看板 |
| --- | --- |
| ![情绪日记](docs/screenshots/05-emotion-diary.png) | ![数据看板](docs/screenshots/03-dashboard.png) |

| 知识库 | 登录页 |
| --- | --- |
| ![知识库](docs/screenshots/06-knowledge.png) | ![登录页](docs/screenshots/02-login.png) |

## 功能特性

### 用户端

- **账号体系**：注册、登录、退出登录，密码使用 BCrypt 加密存储
- **AI 咨询**：创建咨询会话、查看历史会话、删除会话
- **流式对话**：基于 SSE（Server-Sent Events）逐字返回 AI 回复
- **会话情绪分析**：查看单次会话的情绪标签、关键词与建议
- **情绪日记**：记录每日心情、主导情绪、触发事件、睡眠与压力水平
- **异步情绪分析**：日记提交后由后台任务队列异步生成结构化分析结果
- **心理知识库**：分类浏览、文章列表、文章详情阅读

### 管理后台

- **数据看板**：用户总数、活跃用户、日记数、会话数、平均情绪评分、情绪趋势、咨询趋势、用户活跃度
- **知识文章管理**：文章增删改查、封面与图片上传、发布/下线、分类树管理
- **咨询记录**：查看全部用户的会话列表与消息明细
- **情绪日志**：查看并删除用户情绪日记

## 技术栈

### 后端

| 技术 | 版本 / 说明 |
| --- | --- |
| Java | 17 |
| Spring Boot | 3.5.11 |
| MyBatis-Plus | 3.5.17 |
| PostgreSQL | 16 |
| JJWT | 0.12.6（HS256） |
| BCrypt | `spring-security-crypto` |
| 接口文档风格 | REST + 统一 `Result` 响应体 |
| 实时通信 | Spring MVC `SseEmitter` |

### 前端

| 技术 | 版本 / 说明 |
| --- | --- |
| Vue | 3.5（`<script setup>`） |
| Vite | 8 |
| Element Plus | 2.14 |
| Pinia | 4 |
| Vue Router | 4（含登录与角色守卫） |
| Axios | 请求封装与统一拦截 |
| ECharts | 6（后台图表） |
| WangEditor | 富文本文章编辑 |
| Sass | 样式预处理 |

## 系统架构

```text
浏览器（Vue 3 SPA）
        │  /api/*  （开发环境经 Vite 代理）
        ▼
Spring Boot 3
  ├── JWT 拦截器 + UserContext（ThreadLocal）
  ├── REST Controller → Service → MyBatis-Plus → PostgreSQL
  ├── SSE 流式对话（/api/psychological-chat/stream）
  ├── 异步分析任务（ai_analysis_task + 线程池）
  ├── 本地文件存储（uploads/，通过 /files/** 访问）
  └── AiChatClient（当前为 Mock 实现，可替换为真实大模型）
```

- **认证**：登录后签发 JWT，前端在 `Authorization` 头中携带；拦截器解析后将 `userId`、`userType` 写入线程上下文，请求结束后清理。
- **权限**：`user_type = 1` 为普通用户，`user_type = 2` 为管理员；后台接口统一校验管理员身份。
- **异步任务**：日记提交时写入 `ai_analysis_task` 任务，由调度线程异步处理并回填分析结果，避免阻塞提交请求。
- **AI 接缝**：`AiChatClient` 定义了流式对话与情绪分析契约，当前 `app.ai.provider=mock` 使用确定性输出，接入真实模型时新增实现类并切换配置即可，业务代码无需改动。

## 目录结构

```text
.
├── README.md
└── code
    ├── ai-springboot                 # 后端服务
    │   ├── pom.xml
    │   └── src
    │       ├── main
    │       │   ├── java/com/example/aispringboot
    │       │   │   ├── ai/           # AI 客户端抽象与 Mock 实现
    │       │   │   ├── common/       # 统一响应、异常、JWT、用户上下文
    │       │   │   ├── config/       # 拦截器、跨域、MyBatis-Plus、线程池
    │       │   │   ├── controller/   # REST 接口
    │       │   │   ├── DTO/          # 请求与响应对象
    │       │   │   ├── entity/       # 数据库实体
    │       │   │   ├── mapper/       # MyBatis-Plus Mapper
    │       │   │   └── service/      # 业务逻辑
    │       │   └── resources
    │       │       ├── application.yml
    │       │       └── db/schema.sql # 数据库结构脚本
    │       └── test                  # 集成测试与冒烟测试
    └── ai-vue                        # 前端应用
        ├── package.json
        ├── vite.config.js
        └── src
            ├── api/                  # 接口封装
            ├── components/           # 布局、导航、编辑器等公共组件
            ├── config/               # 文件访问基址等运行配置
            ├── router/               # 路由与权限守卫
            ├── stores/               # Pinia 状态
            ├── utils/                # Axios 封装
            └── views/                # 页面
```

## 快速开始

### 环境要求

- JDK 17
- Maven 3.9+（仓库自带 `mvnw` / `mvnw.cmd`）
- Node.js 20+ 与 npm
- PostgreSQL 16

### 1. 初始化数据库

```bash
# 创建数据库
createdb -U postgres mental_health_assistant

# 导入表结构
psql -U postgres -d mental_health_assistant \
  -f code/ai-springboot/src/main/resources/db/schema.sql

# 导入演示数据（3 个账号、17 条日记、5 个会话、7 篇文章）
psql -U postgres -d mental_health_assistant \
  -f code/ai-springboot/src/main/resources/db/seed.sql
```

Windows 下若 `psql` 未加入 PATH，可使用完整路径，例如：

```powershell
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U postgres -d mental_health_assistant `
  -f code/ai-springboot/src/main/resources/db/schema.sql
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U postgres -d mental_health_assistant `
  -f code/ai-springboot/src/main/resources/db/seed.sql
```

### 2. 配置后端

编辑 `code/ai-springboot/src/main/resources/application.yml`，或通过环境变量覆盖：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/mental_health_assistant` | 数据库连接地址 |
| `DB_USERNAME` | `postgres` | 数据库用户名 |
| `DB_PASSWORD` | `123456` | 数据库密码，生产环境必须覆盖 |
| `JWT_SECRET` | 开发默认密钥 | HS256 密钥，生产环境必须覆盖 |
| `UPLOAD_DIR` | `./uploads` | 上传文件存储目录 |
| `AI_PROVIDER` | `mock` | 大模型提供方，接入真实模型后切换 |

### 3. 启动后端

```bash
cd code/ai-springboot
mvn spring-boot:run
```

默认端口 **1236**。健康检查：

```bash
curl http://localhost:1236/actuator/health
```

### 4. 启动前端

```bash
cd code/ai-vue
npm install
npm run dev
```

访问 <http://localhost:5173> 。前端通过 Vite 代理将 `/api` 转发到 `http://localhost:1236`。

### 5. 创建管理员账号

先在前端注册一个普通账号，然后在数据库中提升为管理员：

```sql
UPDATE "user" SET user_type = 2 WHERE username = '你的账号';
```

> PostgreSQL 中 `user` 是保留字，必须写成带双引号的 `"user"`。
>
> 若已导入 `seed.sql`，可直接使用演示账号：`admin / 123456`（管理员）、`test / 123456`、`ces / 123456`。

### 6. 构建前端

```bash
cd code/ai-vue
npm run build      # 产物输出至 dist/
npm run preview    # 本地预览构建产物
```

## 配置说明

| 配置项 | 位置 | 说明 |
| --- | --- | --- |
| 服务端口 | `application.yml` → `server.port` | 默认 1236 |
| 数据库连接 | `application.yml` → `spring.datasource` | PostgreSQL 连接信息 |
| JWT 密钥与有效期 | `application.yml` → `jwt` | HS256，默认 2 小时 |
| AI 提供方 | `application.yml` → `app.ai.provider` | 当前 `mock` |
| 上传目录 | `application.yml` → `app.upload.dir` | 默认 `./uploads` |
| 文件访问前缀 | `application.yml` → `app.file.url-prefix` | 默认 `/files` |
| 前端代理目标 | `vite.config.js` → `server.proxy` | 默认 `http://localhost:1236` |
| 文件访问基址 | `src/config/index.js` → `fileBaseUrl` | 需与后端地址保持一致 |

部署到其他环境时，同步修改 `vite.config.js` 的代理目标与 `src/config/index.js` 的文件基址即可。

## 接口概览

| 模块 | 路径前缀 | 主要接口 |
| --- | --- | --- |
| 用户 | `/api/user` | `POST /login`、`POST /register`、`POST /logout` |
| 心理咨询 | `/api/psychological-chat` | `POST /session/start`、`GET /sessions`、`GET /sessions/{id}/messages`、`DELETE /sessions/{id}`、`GET /session/{id}/emotion`、`POST /stream` |
| 情绪日记 | `/api/emotion-diary` | `POST`（提交）、`GET /admin/page`、`DELETE /admin/{id}` |
| 知识库 | `/api/knowledge` | `GET /category/tree`、`GET /article/page`、`GET /article/{id}`、`POST /article`、`PUT /article/{id}`、`PUT /article/{id}/status`、`DELETE /article/{id}` |
| 数据分析 | `/api/data-analytics` | `GET /overview` |
| 文件 | `/api/file` | `POST /upload` |

接口统一返回结构：

```json
{
  "code": "0",
  "msg": "success",
  "data": {}
}
```

## 数据库结构

共 9 张表：

| 表名 | 说明 |
| --- | --- |
| `user` | 用户与管理员账号 |
| `emotion_diary` | 情绪日记 |
| `ai_analysis_task` | AI 分析任务队列 |
| `consultation_session` | 咨询会话 |
| `consultation_message` | 咨询消息 |
| `knowledge_category` | 知识分类 |
| `knowledge_article` | 知识文章 |
| `sys_file_info` | 上传文件元信息 |
| `user_favorite` | 用户收藏（预留） |

完整 DDL 见 [code/ai-springboot/src/main/resources/db/schema.sql](code/ai-springboot/src/main/resources/db/schema.sql)，演示数据见 [db/seed.sql](code/ai-springboot/src/main/resources/db/seed.sql)。

## 权限模型

| 角色 | `user_type` | 可访问范围 |
| --- | --- | --- |
| 普通用户 | 1 | 前台首页、AI 咨询、情绪日记、知识库 |
| 管理员 | 2 | 后台数据看板、知识文章管理、咨询记录、情绪日志 |

- 登录接口与前台公开接口通过 `@Anonymous` 注解放行，其余接口默认要求 JWT。
- 前端路由守卫基于本地存储中的 `userInfo.userType` 做页面分流，后端接口仍会独立校验权限。
- 用户数据按 `user_id` 隔离；跨用户访问会话会被服务端拒绝。

## 测试

后端包含 8 个测试类、55 个用例，覆盖认证、咨询会话、情绪日记、知识库、数据分析、AI 任务重试与数据库冒烟等链路：

```bash
cd code/ai-springboot
mvn test
```

> 测试连接本地 PostgreSQL。首次运行前请先导入 `schema.sql` 与 `seed.sql`，否则依赖基线数据的用例会失败。

## 已知限制

- AI 回复当前使用 **Mock 实现**，输出为确定性文案，仅用于跑通流式对话与情绪分析契约；接入真实大模型需实现 `AiChatClient` 并切换 `app.ai.provider`。
- 文件存储使用本地磁盘，尚未接入对象存储。
- 未提供容器化部署与 CI 配置。

## 后续可扩展方向

- 接入真实大模型服务（OpenAI / DeepSeek / 通义千问等），保留现有流式契约
- 文件存储迁移至对象存储（OSS / MinIO）
- 增加刷新令牌、账号锁定与操作审计
- 使用 Docker Compose 编排前后端与数据库

## 说明

本项目为个人项目，代码与文档由作者维护。仓库中的数据库密码、JWT 密钥等仅为本地开发默认值，部署前请务必替换。

## 许可证

本项目基于 [MIT License](LICENSE) 开源。
