# Render 临时页面预览

这个方案只发布前端演示，不创建 Java 后端、MySQL 或真实模型服务。
当前预览地址：[https://mindman-preview.onrender.com/](https://mindman-preview.onrender.com/)。
登录页的 `demo / 123456` 是浏览器模拟账号；不要输入真实账号密码或私人记录。
数据使用模拟逻辑，其中部分保存在当前浏览器，其他部分刷新后重置，没有云端持久化保障。

## 部署

1. 登录 [Render 控制台](https://dashboard.render.com/)。
2. 选择 New → Blueprint，连接本仓库的 `main` 分支。
3. 确认使用根目录 `render.yaml`，资源类型为 Static Site。
4. 部署后访问控制台提供的 `onrender.com` 地址。

也可以选择 New → Static Site，手动设置：

| 配置 | 值 |
| --- | --- |
| Repository | `https://github.com/OwenWhw/MindMan-MentalHealth-Assistant` |
| Branch | `main` |
| Root Directory | `code/ai-vue` |
| Build Command | `npm ci && npm run build` |
| Publish Directory | `dist` |
| `NODE_VERSION` | `22` |
| `VITE_API_MODE` | `mock` |
| `VITE_PUBLIC_DEMO` | `true` |

设置 Rewrite：`/*` → `/index.html`。网站主要使用 Hash 路由。

## 完整版需要什么

Java 后端必须连接可持久化的 MySQL，并配置可用的模型服务。Render 免费 Web 服务没有持久化磁盘，不能将临时文件系统当作正式数据库；不建议直接把现有 Docker Compose 栈塞进一个免费实例。

购买阿里云服务器后，可按 [部署手册](./部署手册.md) 部署完整项目，使用真实 API 模式重新构建前端，再更新公开访问地址。

参考：[Render 静态站文档](https://render.com/docs/static-sites)、[免费方案限制](https://render.com/docs/free)。
