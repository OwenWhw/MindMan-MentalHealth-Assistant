<div align="center">

# MindMan · Mental Health Assistant

**A full-stack mental-health web app that connects emotion tracking × AI empathic chat × mindfulness relaxation**

[![Vue](https://img.shields.io/badge/Vue-3.5-42b883?logo=vuedotjs&logoColor=white)](https://vuejs.org/)
[![Vite](https://img.shields.io/badge/Vite-8-646cff?logo=vite&logoColor=white)](https://vitejs.dev/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.16-1f6feb)](https://baomidou.com/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-green)](./LICENSE)
[![CI](https://github.com/OwenWhw/MindMan-MentalHealth-Assistant/actions/workflows/ci.yml/badge.svg)](https://github.com/OwenWhw/MindMan-MentalHealth-Assistant/actions/workflows/ci.yml)

[中文](./README.md) · [Quick Start](#-quick-start) · [Project Structure](#-project-structure) · [API Overview](#-api-overview)

</div>

---

## Overview

MindMan is a self-service mental-health platform for everyday users, bundled with an operations admin console. It does **not** try to replace therapy — it focuses on three everyday needs:

1. **"I can't put my feelings into words."** — the Emotion Garden turns each mood entry into a flower; over time you see your own trend.
2. **"I want to talk but can't start."** — an LLM-powered empathic chat, streaming over SSE, 24/7, with multi-session history.
3. **"Too anxious to sleep."** — a relaxation space with synthesized white/brown noise plus real field recordings (rain, ocean, forest, fireplace), with a floating global player.

> ⚠️ **Disclaimer**: MindMan is a mood-tracking and companionship tool. It provides **no medical diagnosis**. AI replies and scales are for reference only and are not a substitute for professional care.

## Features

### User side

| Module | Description |
| --- | --- |
| Dashboard | Quick mood check-in, weekly emotion insight, AI article recommendations, daily quote |
| Emotion Garden | Every entry grows a flower whose shape/color encodes emotion type & intensity |
| AI Counseling | Empathic LLM conversation with **SSE streaming**, multi-session management & archiving |
| Knowledge Base | 6 categories of psychoeducation articles, read counts, AI recommendations, crawler ingestion |
| Relaxation | Web Audio API synthesized white/brown noise + real recordings, floating player, Canvas ambience |
| Emotion Dashboard | ECharts views: trend, distribution share, trigger-factor analysis |

### Admin console

| Module | Description |
| --- | --- |
| Analytics | Platform-wide overview (`/api/analysis/overview`) |
| Users | List, enable/disable, role assignment |
| Articles | CRUD, publish/unpublish, category tree |
| Consultations | Full session list with message replay |
| Emotion Diary | Paginated search over all mood records |
| Crawler | Manually trigger content ingestion, manage seed sites |

## Tech Stack

**Backend** — Java 17 · Spring Boot 3.4.1 · Spring WebFlux (WebClient + SSE) · Spring Security + JWT (jjwt) · MyBatis-Plus 3.5.16 · MySQL 8 · Redis · Knife4j / OpenAPI 3 · Hutool · Aliyun OSS

**Frontend** — Vue 3.5 (`<script setup>`) · Vite 8 · Pinia 4 · Vue Router 4 (hash) · Element Plus 2.14 · lucide-vue-next · ECharts 6 · Axios · Web Audio API

**AI** — Alibaba Cloud Bailian DashScope (OpenAI-compatible). SiliconFlow is supported as an alternative. With no API key configured, the service gracefully degrades to a local empathic reply script.

## Project Structure

```text
code/
├── backend/          # Spring Boot service
│   ├── pom.xml
│   ├── seed_data.py
│   ├── docs/API_SPEC.md
│   └── src/main/java/com/mindman/
│       ├── common/       # R response wrapper, ResultCode, PageVO, exceptions
│       ├── config/       # Security, WebMvc, MyBatis, WebClient, Redis
│       ├── controller/   # 12 controllers
│       ├── dto/          # request/response DTOs with Bean Validation
│       ├── entity/       # 6 entities
│       ├── interceptor/  # JWT interceptor + ThreadLocal user context
│       ├── mapper/       # MyBatis-Plus mappers
│       ├── service/      # interfaces + impl
│       └── util/         # JwtUtil and helpers
└── ai-vue/           # Vue 3 frontend
    ├── src/api/         # 10 API modules
    ├── src/components/  # 19 components (FloatPlayer, AppNavBar, ...)
    ├── src/router/      # routes + guards
    ├── src/stores/      # app / auth / emotion / player
    └── src/views/       # auth / user / backend
```

## Quick Start

### Prerequisites

JDK 17+ · Maven 3.8+ · Node.js 18+ · MySQL 8.0+ · Redis 5.0+ (optional)

### 1. Initialize the database

```bash
mysql -u root -p < code/backend/src/main/resources/init.sql
```

This creates the `mindman` schema with 6 tables and a default administrator:

| Account | Password | Role |
| --- | --- | --- |
| `admin` | `123456` | `admin` |

### 2. Configure secrets

```bash
cd code/backend/src/main/resources
cp application-local.yml.example application-local.yml
```

`application-local.yml` is git-ignored. Key variables:

| Variable | Required | Description |
| --- | --- | --- |
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DATABASE` | ✅ | Database connection |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | ✅ | Database credentials |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | ⬜ | Defaults to `localhost:6379` |
| `JWT_SECRET` | ✅ | Must be replaced in production (HS256 needs ≥ 256 bits) |
| `AI_BAILIAN_API_KEY` | ⬜ | Alibaba Cloud Bailian key |
| `AI_SILICONFLOW_API_KEY` | ⬜ | SiliconFlow key (alternative provider) |

OS environment variables take precedence over `application-local.yml`. With no AI key set, replies fall back to a local empathic script so the app still works.

### 3. Run the backend

```bash
cd code/backend
mvn spring-boot:run
# or
mvn -DskipTests package && java -jar target/mindman-server-1.0.0.jar
```

- API base: <http://localhost:8080/api>
- Knife4j docs: <http://localhost:8080/doc.html>

### 4. Run the frontend

```bash
cd code/ai-vue
npm install
npm run dev      # http://localhost:5173
npm run build    # -> dist/
```

## API Overview

All endpoints share one envelope:

```json
{ "code": 200, "message": "success", "data": {}, "traceId": "..." }
```

| Group | Base | Sample endpoints |
| --- | --- | --- |
| Auth | `/api/auth` | `POST /login` `POST /register` `GET /me` `PUT /profile` `PUT /password` `POST /logout` |
| Emotion | `/api/emotion` | `GET /garden` `POST /garden` `PUT /garden/{id}` `DELETE /garden/{id}` `GET /diary/page` |
| Insight | `/api/emotion/insight` | `GET /this-week` |
| Chat | `/api/chat` | `GET /sessions` `POST /sessions` `GET /sessions/{id}/messages` `GET /stream` (SSE) `GET /models` |
| Consult | `/api/consult` | `POST /emotion/analyze` |
| Knowledge | `/api/knowledge` | `GET /category/tree` `GET /article/page` `POST /article` `PUT /article/status` |
| Recommend | `/api/articles` | `GET /recommend` |
| Quote | `/api/quote` | `GET /random` |
| Analytics | `/api/analysis` | `GET /overview` |
| Users (admin) | `/api/admin/users` | `PUT /{id}/status` `PUT /{id}/role` `DELETE /{id}` |
| Consult (admin) | `/api/admin/consult` | `GET /sessions` `GET /sessions/{id}/messages` `DELETE /sessions/{id}` |
| Crawler (admin) | `/api/admin/crawler` | `POST /run` `GET /seeds` |

Field-level reference: [`code/backend/docs/API_SPEC.md`](./code/backend/docs/API_SPEC.md) and [`docs/openapi.yaml`](./docs/openapi.yaml).

## Data Model

| Table | Description |
| --- | --- |
| `sys_user` | Users (`username`, `phone`, `email`, BCrypt `password`, `role`, `deleted`) |
| `article_category` | Article categories (`name`, `parent_id`, `sort`) |
| `article` | Knowledge articles (`title`, `content`, `category_id`, `reads`, `status`) |
| `emotion_record` | Mood entries (`user_id`, `emotion_type`, `intensity`, `trigger`, `note`) |
| `chat_session` | Chat sessions (`user_id`, `title`, `archived`) |
| `chat_message` | Chat messages (`session_id`, `role`, `content`) |

All tables use MyBatis-Plus logical deletion via a `deleted` column (0 = active, 1 = deleted).

## Deployment

```bash
cd code/backend
mvn -DskipTests clean package

export MYSQL_HOST=127.0.0.1
export MYSQL_PASSWORD='your-password'
export JWT_SECRET='a-very-long-random-string-at-least-32-bytes'
export AI_BAILIAN_API_KEY='sk-xxx'

java -jar target/mindman-server-1.0.0.jar --spring.profiles.active=dev
```

Serve the frontend `dist/` with any static server and proxy `/api` to port 8080 (Nginx `try_files $uri $uri/ /index.html;` for SPA fallback).

## Conventions

- Every endpoint returns the unified `R<T>` envelope; exceptions are handled globally by `GlobalExceptionHandler`
- The interceptor stores user context in a `ThreadLocal`; read it via `UserContext.getUserId()`
- Only `/api/auth/login`, `/api/auth/register` and `/api/auth/logout` are public — everything else requires a valid JWT
- Passwords are BCrypt-hashed and never written to logs
- Frontend icons come exclusively from `lucide-vue-next` (same-name overrides registered in `main.js`)

## License

[MIT](./LICENSE) © 2026 Wei Haowen
