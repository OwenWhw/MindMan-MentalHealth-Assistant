// API 模式：
// - 'mock'：使用本地模拟数据（后端未就绪时使用，页面可正常预览）
// - 'real'：调用真实后端接口（需启动后端服务）
// 可通过项目根目录 .env 文件中的 VITE_API_MODE 切换
export const API_MODE = import.meta.env.VITE_API_MODE || 'mock'
