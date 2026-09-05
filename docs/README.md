# 文档目录

本目录存放项目级设计文档；后端接口的权威规范放在 `code/backend/docs/`。

| 文档 | 语言 | 状态 | 说明 |
| --- | --- | --- | --- |
| [`openapi.yaml`](./openapi.yaml) | — | ✅ 现行 | OpenAPI 3 规范文件，可导入 Apifox / Postman |
| [`接口文档.md`](./接口文档.md) | 中文 | ⚠️ 早期设计稿 | 字段命名 `msg` 为历史约定，实际实现已统一为 `message`，请以 API_SPEC 为准 |
| [`../code/backend/docs/API_SPEC.md`](../code/backend/docs/API_SPEC.md) | 中文 | ✅ 现行 | 接口规范：响应结构、状态码、鉴权、分页、各模块接口 |

## 快速导航

- 项目总览与启动方式 → [`../README.md`](../README.md)
- 英文说明 → [`../README_EN.md`](../README_EN.md)
- 数据库建表脚本 → [`../code/backend/src/main/resources/init.sql`](../code/backend/src/main/resources/init.sql)
- 本地私密配置模板 → [`../code/backend/src/main/resources/application-local.yml.example`](../code/backend/src/main/resources/application-local.yml.example)
