import { createApp } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import * as LucideIcons from 'lucide-vue-next'
import 'element-plus/dist/index.css'
// 离线预览版（VITE_PREVIEW=true）不打包字体，直接使用系统字体，减小体积
if (!import.meta.env.VITE_PREVIEW) {
  await import('@fontsource/inter/400.css')
  await import('@fontsource/inter/500.css')
  await import('@fontsource/inter/600.css')
  await import('@fontsource/inter/700.css')
  await import('@fontsource/noto-sans-sc/400.css')
  await import('@fontsource/noto-sans-sc/500.css')
  await import('@fontsource/noto-sans-sc/600.css')
  await import('@fontsource/noto-sans-sc/700.css')
  await import('@fontsource/noto-serif-sc/400.css')
  await import('@fontsource/noto-serif-sc/600.css')
}
import './style.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)
const pinia = createPinia()
setActivePinia(pinia)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// ═══ lucide 图标：按同名覆盖 Element Plus 注册 ═══
// 模板中 <el-icon><Cherry /></el-icon> 与 :is="'ChatDotRound'" 都会解析到 lucide 组件
const LUCIDE_ICON_MAP = {
  // 常用映射（Element Plus 图标名 → lucide 组件名）
  ArrowDown: 'ChevronDown',
  ArrowLeft: 'ArrowLeft',
  ArrowRight: 'ArrowRight',
  Bell: 'Bell',
  Bottom: 'ChevronDown',
  Briefcase: 'Briefcase',
  Calendar: 'Calendar',
  ChatDotRound: 'MessageCircle',
  ChatLineSquare: 'MessageSquare',
  Check: 'Check',
  Cherry: 'Flower2', // 情绪花园：樱桃 → 花朵更贴合
  Clock: 'Clock',
  Close: 'X',
  Collection: 'FolderOpen',
  Cpu: 'Cpu',
  DArrowLeft: 'ChevronsLeft',
  DataAnalysis: 'BarChart3',
  DataLine: 'TrendingUp',
  Delete: 'Trash2',
  Document: 'FileText',
  Edit: 'SquarePen',
  Fire: 'Flame',
  Headset: 'Headphones',
  HomeFilled: 'Home',
  Link: 'Link',
  Loading: 'Loader2',
  Mug: 'Coffee',
  Mute: 'VolumeX',
  Notebook: 'Notebook',
  Plus: 'Plus',
  Reading: 'BookOpen',
  Refresh: 'RefreshCw',
  Right: 'ChevronRight',
  Search: 'Search',
  Setting: 'Settings',
  Sparkles: 'Sparkles',
  Sunny: 'Sun',
  Sunrise: 'Sunrise',
  SwitchButton: 'Power',
  Timer: 'Timer',
  User: 'User',
  VideoPause: 'Pause',
  VideoPlay: 'Play',
  View: 'Eye',
  Warning: 'TriangleAlert',
  Watermelon: 'Apple',
  WindPower: 'Wind',
  circle: 'Circle'
}
for (const [epName, lucideName] of Object.entries(LUCIDE_ICON_MAP)) {
  const comp = LucideIcons[lucideName]
  if (comp) app.component(epName, comp)
}

app.use(pinia).use(ElementPlus).use(router).mount('#app')
