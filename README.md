<div align="center">

# MindMan · 心理健康助手

**一个把「情绪记录 × AI 共情对话 × 正念放松」串起来的全栈心理健康 Web 应用**

[![Vue](https://img.shields.io/badge/Vue-3.5-42b883?logo=vuedotjs&logoColor=white)](https://vuejs.org/)
[![Vite](https://img.shields.io/badge/Vite-8-646cff?logo=vite&logoColor=white)](https://vitejs.dev/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.16-1f6feb)](https://baomidou.com/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-green)](./LICENSE)
[![CI](https://github.com/OwenWhw/MindMan-MentalHealth-Assistant/actions/workflows/ci.yml/badge.svg)](https://github.com/OwenWhw/MindMan-MentalHealth-Assistant/actions/workflows/ci.yml)

[English](./README_EN.md) · [快速开始](#-快速开始) · [项目结构](#-项目结构) · [API 概览](#-api-概览)

</div>

---

## 一、项目简介

MindMan 是一个面向普通用户的心理健康自助平台，同时提供运营侧的管理后台。

它想解决的不是"替代心理咨询"，而是三件更日常的事：

1. **说不清自己怎么了** —— 用情绪花园把心情画成花，长期看趋势；
2. **想找人聊聊但开不了口** —— 接入大模型做共情式 AI 对话，7×24 在线；
3. **焦虑到睡不着** —— 白噪音放松空间，随时进入一段安静。

> ⚠️ **免责声明**：本项目定位为情绪记录与陪伴工具，**不提供任何医学诊断**。相关量表与 AI 回复仅供参考，不能替代专业心理诊疗。

---

## 二、功能特性

### 用户端

| 模块 | 说明 |
| --- | --- |
| 🏠 首页洞察 | 今日心情速记、本周情绪洞察、AI 个性化文章推荐、治愈语录 |
| 🌱 情绪花园 | 每次记录生成一朵花，花型/颜色映射情绪类型与强度，可视化回看 |
| 💬 AI 咨询 | 基于大模型的共情对话，**SSE 流式输出**，多轮会话管理与历史归档 |
| 📚 知识文章 | 6 大分类心理科普，支持阅读量统计、AI 推荐、爬虫内容入库 |
| 🎧 放松空间 | 白噪音 / 棕色噪音（Web Audio API 实时合成）+ 雨声、海浪、森林、壁炉等真实录音，全局浮球播放器 |
| 📊 情绪仪表盘 | ECharts 多维度图表：情绪趋势、分布占比、触发因素分析 |

### 管理后台

| 模块 | 说明 |
| --- | --- |
| 数据分析 | 平台整体运营概览（`/api/analysis/overview`） |
| 用户管理 | 用户列表、启停账号、角色分配 |
| 知识文章 | 文章 CRUD、上下架、分类树管理 |
| 咨询记录 | 查看全部会话及逐条消息回溯 |
| 情绪日志 | 全平台情绪记录分页检索 |
| 爬虫管理 | 手动触发心理科普内容抓取、维护种子站点 |

---

## 三、技术栈

### 后端

| 类别 | 选型 | 说明 |
| --- | --- | --- |
| 基础框架 | Spring Boot 3.4.1 | Java 17 |
| ORM | MyBatis-Plus 3.5.16 | 分页插件、逻辑删除、自动填充 |
| 数据库 | MySQL 8.0 | 6 张业务表 |
| 缓存 | Redis | Spring Data Redis |
| 安全 | Spring Security + JWT (jjwt 0.12.x) | BCrypt 加密，Token 有效期 7 天 |
| 响应式 | Spring WebFlux (WebClient) | 调用大模型 API + SSE 流式推送 |
| AI | 阿里云百炼 DashScope（OpenAI 兼容） | 备选：硅基流动；未配置时自动降级为本地共情话术 |
| 对象存储 | 阿里云 OSS SDK | 文件上传 |
| 接口文档 | Knife4j 4.5.0 / OpenAPI 3 | `/doc.html` |
| 工具 | Hutool、Lombok、Validation、AOP | |

### 前端

| 类别 | 选型 |
| --- | --- |
| 框架 | Vue 3.5（`<script setup>`）+ Vite 8 |
| 状态 | Pinia 4（`app` / `auth` / `emotion` / `player`） |
| 路由 | Vue Router 4（Hash 模式 + 路由守卫） |
| UI | Element Plus 2.14 + lucide-vue-next（同名义覆盖） |
| 图表 | ECharts 6 |
| 音频 | Web Audio API（噪声合成）+ HTMLAudioElement（真实录音） |
| 请求 | Axios（统一拦截器 + JWT 注入） |

---

## 四、项目结构

```text
MindMan-MentalHealth-Assistant/
├── code/
│   ├── backend/                       # Spring Boot 后端
│   │   ├── pom.xml
│   │   ├── seed_data.py               # 情绪记录种子数据生成脚本
│   │   ├── docs/API_SPEC.md           # 接口规范
│   │   └── src/main/
│   │       ├── java/com/mindman/
│   │       │   ├── common/            # R 统一响应、ResultCode、PageVO、异常体系
│   │       │   ├── config/            # Security / WebMvc / MyBatis / WebClient / Redis
│   │       │   ├── controller/        # 12 个控制器
│   │       │   ├── dto/               # 请求/响应 DTO（含 JSR-303 校验）
│   │       │   ├── entity/            # 6 个实体
│   │       │   ├── interceptor/       # JWT 拦截 + ThreadLocal 用户上下文
│   │       │   ├── mapper/            # MyBatis-Plus Mapper
│   │       │   ├── service/           # 业务接口 + impl
│   │       │   ├── task/              # 定时任务
│   │       │   └── util/              # JwtUtil 等工具类
│   │       └── resources/
│   │           ├── application.yml        # 主配置
│   │           ├── application-dev.yml    # 开发环境（已脱敏）
│   │           ├── application-local.yml  # 本地私密配置（不入库）
│   │           └── init.sql               # 建表 + 初始管理员
│   └── ai-vue/                        # Vue 3 前端
│       ├── src/api/                   # 10 个接口模块
│       ├── src/components/            # 19 个组件（含 FloatPlayer、AppNavBar）
│       ├── src/router/                # 路由 + 守卫
│       ├── src/stores/                # Pinia stores
│       ├── src/views/
│       │   ├── auth/                  # 登录
│       │   ├── user/                  # 首页 / 咨询 / 花园 / 文章 / 放松
│       │   └── backend/               # 管理后台 6 个页面
│       └── public/sounds/             # 白噪音音频资源
├── docs/                              # 接口文档 / OpenAPI 规范
├── scripts/                           # 辅助脚本
├── tools/serve_dist.py                # 带 gzip 与 /api 代理的静态预览服务器
└── README.md
```

---

## 五、快速开始

### 5.1 环境要求

| 组件 | 版本 |
| --- | --- |
| JDK | 17+ |
| Maven | 3.8+ |
| Node.js | 18+（推荐 20 LTS） |
| MySQL | 8.0+ |
| Redis | 5.0+（可选，未启动时部分缓存功能降级） |

### 5.2 数据库初始化

```bash
mysql -u root -p < code/backend/src/main/resources/init.sql
```

脚本会创建 `mindman` 库与 6 张表，并插入初始管理员：

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `123456` | `admin` |

> ⚠️ 上述为**本地演示账号**，仅用于快速体验。若部署到公网，请务必在登录后立即修改密码（或直接修改数据库中的 BCrypt 哈希），避免被扫描利用。

### 5.3 配置

复制配置模板并填入你的真实密钥（`application-local.yml` 已被 `.gitignore` 忽略，不会被提交）：

```bash
cd code/backend/src/main/resources
cp application-local.yml.example application-local.yml
```

| 变量 | 必填 | 说明 |
| --- | --- | --- |
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DATABASE` | ✅ | 数据库连接信息 |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | ✅ | 数据库账号 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | ⬜ | Redis，默认 `localhost:6379` |
| `JWT_SECRET` | ✅ | 生产环境**必须**更换，HS256 要求 ≥ 256 bit |
| `AI_BAILIAN_API_KEY` | ⬜ | 阿里云百炼 Key，[点此申请](https://bailian.console.aliyun.com/) |
| `AI_SILICONFLOW_API_KEY` | ⬜ | 硅基流动 Key（备选 provider） |

> 也可以直接用操作系统环境变量注入，优先级高于 `application-local.yml`。
>
> 💡 未配置任何 AI Key 时，服务会自动降级为**本地共情话术回复**，保证功能可用、不报错。

### 5.4 启动后端

```bash
cd code/backend
mvn spring-boot:run
# 或打包运行
mvn -DskipTests package && java -jar target/mindman-server-1.0.0.jar
```

服务启动后：

- API 基址：<http://localhost:8080/api>
- Knife4j 接口文档：<http://localhost:8080/doc.html>

### 5.5 启动前端

```bash
cd code/ai-vue
npm install
npm run dev
```

访问 <http://localhost:5173>。

构建产物：

```bash
npm run build          # 输出到 dist/
```

如需带 gzip 与 `/api` 反向代理的本地预览服务器：

```bash
python tools/serve_dist.py
```

---

## 六、API 概览

统一响应结构：

```json
{ "code": 200, "message": "success", "data": {}, "traceId": "..." }
```

| 分组 | 基址 | 代表接口 |
| --- | --- | --- |
| 认证 | `/api/auth` | `POST /login` `POST /register` `GET /me` `PUT /profile` `PUT /password` `POST /logout` |
| 情绪 | `/api/emotion` | `GET /garden` `POST /garden` `PUT /garden/{id}` `DELETE /garden/{id}` `GET /diary/page` `GET /diary/{id}` |
| 情绪洞察 | `/api/emotion/insight` | `GET /this-week` |
| AI 咨询 | `/api/chat` | `GET /sessions` `POST /sessions` `GET /sessions/{id}/messages` `GET /stream`（SSE） `GET /models` |
| 咨询分析 | `/api/consult` | `POST /emotion/analyze` |
| 知识库 | `/api/knowledge` | `GET /category/tree` `GET /article/page` `POST /article` `PUT /article/status` |
| 文章推荐 | `/api/articles` | `GET /recommend` |
| 语录 | `/api/quote` | `GET /random` |
| 数据分析 | `/api/analysis` | `GET /overview` |
| 用户管理 | `/api/admin/users` | `PUT /{id}/status` `PUT /{id}/role` `DELETE /{id}` |
| 咨询管理 | `/api/admin/consult` | `GET /sessions` `GET /sessions/{id}/messages` `DELETE /sessions/{id}` |
| 爬虫管理 | `/api/admin/crawler` | `POST /run` `GET /seeds` |

完整字段级说明见 [`code/backend/docs/API_SPEC.md`](./code/backend/docs/API_SPEC.md) 与 [`docs/openapi.yaml`](./docs/openapi.yaml)。

---

## 七、数据模型

| 表 | 说明 | 关键字段 |
| --- | --- | --- |
| `sys_user` | 用户 | `username` `phone` `email` `password`(BCrypt) `role` `deleted` |
| `article_category` | 文章分类 | `name` `parent_id` `sort` |
| `article` | 知识文章 | `title` `content` `category_id` `reads` `status` |
| `emotion_record` | 情绪记录 | `user_id` `emotion_type` `intensity` `trigger` `note` |
| `chat_session` | 对话会话 | `user_id` `title` `archived` |
| `chat_message` | 对话消息 | `session_id` `role` `content` |

所有业务表均启用 MyBatis-Plus **逻辑删除**（`deleted` 字段，0 未删 / 1 已删）。

---

## 八、部署

### 后端

```bash
cd code/backend
mvn -DskipTests clean package

# 生产环境变量示例（Linux / macOS）
export MYSQL_HOST=127.0.0.1
export MYSQL_PASSWORD='your-password'
export JWT_SECRET='a-very-long-random-string-at-least-32-bytes'
export AI_BAILIAN_API_KEY='sk-xxx'

java -jar target/mindman-server-1.0.0.jar --spring.profiles.active=dev
```

### 前端

`npm run build` 后把 `dist/` 部署到任意静态服务器，用 Nginx 反代 `/api` 到 8080：

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8080;
}
location / {
    try_files $uri $uri/ /index.html;
}
```

---

## 九、文档索引

| 文档 | 内容 |
| --- | --- |
| [`code/backend/docs/API_SPEC.md`](./code/backend/docs/API_SPEC.md) | 接口规范（当前版本，字段以 `code/message/data` 为准） |
| [`docs/openapi.yaml`](./docs/openapi.yaml) | OpenAPI 3 规范文件 |
| [`docs/接口文档.md`](./docs/接口文档.md) | 早期设计稿（字段 `msg` 为历史命名，实际实现为 `message`） |
| [`docs/README.md`](./docs/README.md) | 文档目录说明 |

---

## 十、开发约定

- 所有接口返回统一 `R<T>` 包装，异常由 `GlobalExceptionHandler` 统一兜底
- 用户上下文通过拦截器写入 `ThreadLocal`，控制器内用 `UserContext.getUserId()` 读取
- 拦截器**只放行** `/api/auth/login`、`/api/auth/register`、`/api/auth/logout`，其余接口必须携带有效 JWT
- 密码统一 BCrypt 加密存储，明文不落库、不写日志
- 前端图标统一使用 `lucide-vue-next`，已在 `main.js` 中做同名覆盖注册

---

## 十一、License

[MIT](./LICENSE) © 2026 魏浩文
