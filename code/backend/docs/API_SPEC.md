# MindMan API 接口规范

> 版本 1.0.0 | Spring Boot 3.4 + MyBatis-Plus + MySQL + JWT

---

## 一、基础约定

### 1.1 Base URL
```
本地开发: http://localhost:8080
生产环境: https://api.mindman.cn
```

### 1.2 统一响应格式

所有接口返回统一 `R<T>` 结构：

```json
{
  "code": 200,       // 200 成功 | 4xx 客户端错误 | 5xx 服务端错误
  "message": "success",
  "data": {}         // 具体业务数据
}
```

分页接口 `data` 内使用 `PageVO<T>`：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 128,
    "page": 1,
    "pageSize": 10,
    "pages": 13,
    "list": [...]
  }
}
```

### 1.3 认证方式

Header 携带 JWT Token：

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

Token 有效期 7 天，过期后返回 `401` 需重新登录。

### 1.4 HTTP 动词约定

| 方法   | 用途             | 幂等 |
|--------|-----------------|------|
| GET    | 查询资源         | ✓   |
| POST   | 创建新资源       | ✗   |
| PUT    | 全量更新         | ✓   |
| DELETE | 删除             | ✓   |

### 1.5 命名规则

- URL：小写 + 短横线，如 `/chat-sessions`
- 请求/响应字段：驼峰，如 `publishedAt`
- 数据库字段：下划线，如 `published_at`
- 分页参数固定：`page`(从1开始) + `pageSize`

---

## 二、错误码定义

| 状态码 | 含义               |
|--------|-------------------|
| 200    | 成功               |
| 400    | 请求参数校验失败    |
| 401    | 未登录 / Token 过期 |
| 403    | 无权限 / 账号禁用   |
| 404    | 资源不存在          |
| 500    | 服务器内部错误      |

---

## 三、接口列表

### 3.1 认证模块 `/api/auth`

#### `POST /api/auth/login`

登录

- **Request**
  ```json
  { "username": "admin", "password": "admin123" }
  ```
- **Response**
  ```json
  {
    "code": 200, "message": "success",
    "data": { "userId": 1, "username": "admin", "nickname": "管理员", "role": "admin", "token": "eyJ..." }
  }
  ```

#### `POST /api/auth/register`（预留）

注册新用户

- **Request**
  ```json
  { "username": "owen", "password": "123456", "nickname": "Owen" }
  ```

#### `POST /api/auth/logout`

退出登录（客户端删除 Token，服务端仅记录）

---

### 3.2 文章模块 `/api/articles`

#### `GET /api/articles`

文章分页列表

| 参数       | 类型   | 默认 | 说明         |
|-----------|--------|------|-------------|
| page      | int    | 1    | 页码         |
| pageSize  | int    | 10   | 每页条数     |
| categoryId| long   | -    | 按分类筛选   |
| keyword   | string | -    | 标题/标签搜索 |
| status    | int    | 1    | 1=已发布     |

- **Response** `PageVO<Article>`
  ```json
  { "total": 128, "page": 1, "pageSize": 10, "pages": 13, "list": [...] }
  ```

#### `GET /api/articles/{id}`

文章详情（阅读量 +1）

- **Response**
  ```json
  {
    "id": 2, "title": "正念练习入门指南",
    "categoryId": 3, "categoryName": "情绪管理",
    "author": "MindMan", "reads": 28,
    "summary": "简介...", "content": "正文...",
    "tags": ["正念","冥想"],
    "publishTime": "2025-09-06 13:10:00"
  }
  ```

#### `GET /api/articles/categories`

文章分类列表（树形结构，最多两级）

- **Response**
  ```json
  [
    { "id": 1, "name": "人际关系", "children": [] },
    { "id": 2, "name": "压力缓解", "children": [] }
  ]
  ```

---

### 3.3 情绪模块 `/api/emotions`

#### `POST /api/emotions`

记录当日心情

- **Request**
  ```json
  { "emotion": "开心", "emotionIcon": "😄", "emotionScore": 5, "note": "阳光很好" }
  ```

#### `GET /api/emotions/today`

获取今日已记录的心情

- **Response** `EmotionRecord | null`

#### `GET /api/emotions/week`

本周情绪趋势（最近 7 天）

- **Response**
  ```json
  [
    { "recordDate": "2026-08-01", "emotion": "开心", "emotionScore": 5 },
    { "recordDate": "2026-08-02", "emotion": "焦虑", "emotionScore": 2 }
  ]
  ```

#### `GET /api/emotions/garden`

情绪花园数据（按日期展示所有记录）

---

### 3.4 聊天模块 `/api/chat`

#### `GET /api/chat/sessions`

会话历史分页

#### `POST /api/chat/sessions`

创建新会话

- **Request** `{ "title": "关于失眠的咨询" }`

#### `GET /api/chat/sessions/{id}/messages`

获取某个会话的消息列表（时间顺序）

- **Response** `List<ChatMessage>`

#### `POST /api/chat/sessions/{id}/messages`

发送消息（后端调用 AI 并记录回复）

- **Request** `{ "content": "最近总是失眠..." }`
- **Response** `{ "role": "assistant", "content": "我先教你一个深呼吸的方法...", "emotion": "{\"压力\":55,\"焦虑\":72,\"睡眠风险\":80}" }`

#### `DELETE /api/chat/sessions/{id}`

删除会话

---

### 3.5 AI 分析模块 `/api/analysis`

#### `POST /api/analysis/emotion`

分析最近对话中的情绪维度

- **Response**
  ```json
  {
    "emotion": "焦虑",
    "emotionIcon": "😰",
    "emotionStar": 2,
    "dimensions": { "压力": 55, "焦虑": 72, "睡眠风险": 80 }
  }
  ```

---

## 四、前端对接约定

| 前端功能         | 对应 API                     |
|-----------------|-----------------------------|
| 登录/注册        | `POST /api/auth/login`      |
| 首页文章推荐      | `GET /api/articles?pageSize=4` |
| 文章列表页        | `GET /api/articles?keyword=&categoryId=` |
| 文章详情页        | `GET /api/articles/:id`      |
| 情绪花园-种花     | `POST /api/emotions`        |
| 情绪花园-查看     | `GET /api/emotions/garden`  |
| AI咨询-发消息     | `POST /api/chat/sessions/:id/messages` |
| AI咨询-历史会话   | `GET /api/chat/sessions`    |
| AI分析面板       | `POST /api/analysis/emotion`|

---

## 五、开发规范

### 5.1 包结构

```
com.mindman
├── config/          # 配置类
├── controller/      # 控制器（薄层，只做参数校验和调用 service）
├── service/         # 服务接口
│   └── impl/        # 服务实现
├── mapper/          # MyBatis-Plus Mapper 接口
├── entity/          # 数据库实体（映射表字段）
├── dto/             # 请求 DTO / 响应 VO
├── common/          # 通用类
│   ├── R.java       # 统一响应
│   ├── exception/   # 异常
│   └── page/        # 分页
└── util/            # 工具类
```

### 5.2 分层职责

| 层        | 职责                               | 禁止                          |
|-----------|-----------------------------------|------------------------------|
| Controller | 参数校验 / 调用 Service / 返回 R  | 写业务逻辑、直接调 Mapper      |
| Service   | 业务逻辑 / 事务管理               | 处理 HTTP 请求/响应           |
| Mapper    | 单表 CRUD                        | 写复杂业务逻辑                |

### 5.3 命名约定

- **Controller 类**: `XxxController`
- **Service 接口**: `XxxService`
- **Service 实现**: `XxxServiceImpl`
- **Mapper 接口**: `XxxMapper`
- **Entity 类**: 数据库表名驼峰，如 `chat_session` → `ChatSession`
- **DTO 类**: 入参用 `XxxDTO`，出参用 `XxxVO`

### 5.4 Git 分支

```
main        # 生产分支
develop     # 开发分支
feature/*   # 功能分支（如 feature/article-api）
fix/*       # 修复分支
```
