import { createRouter, createWebHashHistory } from 'vue-router'
import BackendLayout from '@/components/BackendLayout.vue'
import { useAuthStore } from '@/stores/auth'


const backendRoutes = [
  {
    path: '/back',
    component: BackendLayout,
    children: [
      {
        path: '/back/index',
        meta: { title: '数据分析' },
        component: () => import('@/views/backend/index.vue')
      },
      {
        path: '/back/articles',
        meta: { title: '知识文章' },
        component: () => import('@/views/backend/articles.vue')
      },
      {
        path: '/back/categories',
        meta: { title: '分类管理' },
        component: () => import('@/views/backend/categories.vue')
      },
      {
        path: '/back/users',
        meta: { title: '用户管理' },
        component: () => import('@/views/backend/users.vue')
      },
      {
        path: '/back/records',
        meta: { title: '咨询记录' },
        component: () => import('@/views/backend/records.vue')
      },
      {
        path: '/back/diaries',
        meta: { title: '情绪日志' },
        component: () => import('@/views/backend/diaries.vue')
      },
    ]
  }
]

const routes = [
  { path: '/', redirect: '/back/index' },
  {
    path: '/login',
    component: () => import('@/views/auth/LoginView.vue')
  },
  {
    path: '/register',
    redirect: '/login'
  },
  {
    path: '/home',
    component: () => import('@/components/UserLayout.vue'),
    children: [
      {
        path: '',
        component: () => import('@/views/user/HomeView.vue')
      },
      {
        path: 'consult',
        component: () => import('@/views/user/ConsultView.vue')
      }
    ]
  },
  {
    path: '/home/articles',
    component: () => import('@/views/user/ArticlesView.vue')
  },
  {
    path: '/home/articles/:id',
    component: () => import('@/views/user/ArticleDetailView.vue')
  },
  {
    path: '/consult',
    component: () => import('@/views/user/ConsultView.vue')
  },
  {
    path: '/garden',
    component: () => import('@/views/user/GardenView.vue')
  },
  {
    path: '/relax',
    component: () => import('@/views/user/RelaxView.vue')
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
  ...backendRoutes
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// 路由守卫：后台页面需要登录，已登录用户访问登录/注册页自动回后台
router.beforeEach((to) => {
  const authStore = useAuthStore()
  const isLoggedIn = !!authStore.token

  // 管理后台：仅系统管理员可访问
  if (to.path.startsWith('/back') && !isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path.startsWith('/back') && !authStore.isAdmin) {
    return { path: '/home' }
  }

  // 用户界面：需登录后访问
  if (
    (to.path.startsWith('/home') ||
      to.path.startsWith('/consult') ||
      to.path.startsWith('/garden') ||
      to.path.startsWith('/relax')) &&
    !isLoggedIn
  ) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if ((to.path === '/login' || to.path === '/register') && isLoggedIn) {
    return authStore.isAdmin ? { path: '/back/index' } : { path: '/home' }
  }

  return true
})

export default router
