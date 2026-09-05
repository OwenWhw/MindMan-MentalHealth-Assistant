# MindMan 前端（ai-vue）

基于 Vue 3 + Vite + Pinia 的心理健康助手用户端与管理后台。

## 技术栈

Vue 3.5（`<script setup>`）· Vite 8 · Pinia 4 · Vue Router 4（Hash 模式）· Element Plus 2.14 · lucide-vue-next · ECharts 6 · Axios · Web Audio API

## 命令

```bash
npm install      # 安装依赖
npm run dev      # 开发服务器 http://localhost:5173
npm run build    # 构建到 dist/
npm run preview  # 预览构建产物
```

## 目录

```text
src/
├── api/         # 接口封装：auth / emotion / consult / knowledge / quote / analysis / admin / file / config / mock
├── assets/      # 样式与静态资源
├── components/  # AppNavBar、FloatPlayer、BackendLayout、UserLayout、BaseChart 等
├── router/      # 路由表 + 权限守卫
├── stores/      # app / auth / emotion / player
├── utils/       # request 等工具
└── views/
    ├── auth/       # 登录
    ├── user/       # 首页 / 咨询 / 情绪花园 / 文章 / 文章详情 / 放松空间
    └── backend/    # 数据分析 / 知识文章 / 分类 / 用户 / 咨询记录 / 情绪日志
public/sounds/   # 白噪音与真实录音音频
```

## 环境变量

| 变量 | 说明 |
| --- | --- |
| `VITE_API_MODE` | `mock` 时使用本地模拟数据，便于无后端联调；留空则请求真实接口 |

配置见 `.env`，离线预览构建见 `.env.preview`。

## 说明

- 图标统一使用 `lucide-vue-next`，并在 `src/main.js` 中对 Element Plus 同名图标做覆盖注册
- 白噪音：白噪/棕噪由 Web Audio API 实时合成，雨声、海浪、森林、壁炉为本地音频文件
- 播放器状态存于 `stores/player.js`，`FloatPlayer` 全局常驻
- 路由守卫：`/back/**` 需登录且角色为管理员，用户端页面需登录
