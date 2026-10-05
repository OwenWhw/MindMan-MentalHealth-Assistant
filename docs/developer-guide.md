# MindMan 开发与部署说明

[返回项目介绍](../README.md) · [用户指南](./user-guide.md)

## 三、技术栈

### 后端

| 类别 | 选型 | 说明 |
| --- | --- | --- |
| 基础框架 | Spring Boot 3.4.1 | Java 17 |
| 微服务 | Spring Cloud Alibaba + Nacos | 服务注册与发现；本地单体开发可关闭 |
| ORM | MyBatis-Plus 3.5.16 | 分页插件、逻辑删除、自动填充 |
| 数据库 | MySQL 8.0 | 业务表及增量迁移表 |
| 缓存 | Redis | Spring Data Redis + Spring Cache；分类树缓存 1 小时，写操作自动失效 |
| 安全 | Spring Security + JWT (jjwt 0.12.x) | BCrypt 加密，Token 有效期 7 天 |
| 响应式 | Spring WebFlux (WebClient) | 调用大模型 API + SSE 流式推送 |
| AI | 阿里云百炼 DashScope（OpenAI 兼容） | 备选：硅基流动；可连接 Ollama 本地模型；无可用模型时明确返回不可用 |
| Python Agent（可选） | FastAPI + OpenAI 兼容接口 | 仅回看情绪花园当前一条记录；部署与验证见 [说明](../code/python-agent/README.md) |
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
| Node.js | 22 LTS |
| MySQL | 8.0+ |
| Redis | 5.0+（可选，未启动时部分缓存功能降级） |
| Nacos | 2.5+（容器化演示 Spring Cloud 服务注册时需要） |

### 5.2 数据库初始化

```bash
mysql -u root -p < code/backend/src/main/resources/init.sql
```

现有数据库还需要按文件名顺序执行 `code/backend/src/main/resources/db/migration/` 中的增量脚本，以补齐会话总结、文章同步状态及评分来源字段。执行前备份数据库；不要对已有数据重新执行初始化脚本。

脚本会创建 `mindman` 库与 基础业务表，并插入初始管理员：

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
> AI 功能需要可用的云端模型或 Ollama 服务。未配置可用模型时会提示不可用，不会以固定话术冒充模型回复。

### 5.3.1 Redis 缓存与 Spring Cloud

- `GET /api/knowledge/category/tree` 使用 Spring Cache 缓存到 Redis，TTL 为 1 小时；新增、编辑、删除分类或文章时会自动清除缓存，下一次读取回源 MySQL。
- 后端已接入 Spring Cloud Alibaba Nacos Discovery。普通本地开发保持 `NACOS_ENABLED=false`；用 Docker Compose 启动时，后端会自动注册为 `mindman-server`，可在 Nacos 控制台查看。

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

完整字段级说明见 [`code/backend/docs/API_SPEC.md`](../code/backend/docs/API_SPEC.md) 与 [`docs/openapi.yaml`](./openapi.yaml)。

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

### Docker Compose（推荐演示方式）

Docker Desktop 启动后，在项目根目录执行：

```bash
copy .env.example .env
# 编辑 .env，配置 JWT_SECRET、数据库密码及可用的模型服务
docker compose up -d mysql redis nacos
# 等待数据库健康后执行（Bash 环境）
bash deploy/migrate.sh
docker compose up -d --build
```

Linux 云服务器首次安装可使用 `bash deploy/deploy.sh`，数据库健康后补执行迁移；已有部署更新使用 `bash deploy/update.sh`，会先备份数据库并执行迁移。脚本位置均相对于项目根目录。

访问：前端 `http://localhost`、后端 API `http://localhost:8080/api`、Nacos 控制台 `http://localhost:8848/nacos`（默认账号密码 `nacos/nacos`）。

该编排会启动 MySQL、Redis、Nacos、Spring Boot 后端和 Vue 前端共 5 个容器。查看服务注册：进入 Nacos 控制台后，选择“服务管理 → 服务列表”，可见 `mindman-server`。

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
| [`code/backend/docs/API_SPEC.md`](../code/backend/docs/API_SPEC.md) | 接口规范（当前版本，字段以 `code/message/data` 为准） |
| [`docs/openapi.yaml`](./openapi.yaml) | OpenAPI 3 规范文件 |
| [`docs/接口文档.md`](./接口文档.md) | 早期设计稿（字段 `msg` 为历史命名，实际实现为 `message`） |
| [`docs/README.md`](./README.md) | 文档目录说明 |
| [`tests/README.md`](../tests/README.md) | JMeter → Postman → Playwright 一键测试说明 |

---

## 十、开发约定

- 所有接口返回统一 `R<T>` 包装，异常由 `GlobalExceptionHandler` 统一兜底
- 用户上下文通过拦截器写入 `ThreadLocal`，控制器内用 `UserContext.getUserId()` 读取
- 拦截器**只放行** `/api/auth/login`、`/api/auth/register`、`/api/auth/logout`，其余接口必须携带有效 JWT
- 密码统一 BCrypt 加密存储，明文不落库、不写日志
- 前端图标统一使用 `lucide-vue-next`，已在 `main.js` 中做同名覆盖注册

---

## 十一、License

[MIT](../LICENSE) © 2026 魏浩文


