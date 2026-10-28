import { createRouter, createWebHistory } from 'vue-router'
import { ensureSession, isAdmin, sessionState } from '../utils/session'
import HomeView from '../views/HomeView.vue'

const ModuleView = () => import('../views/ModuleView.vue')

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true, bare: true, title: '登录' },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/RegisterView.vue'),
    meta: { public: true, bare: true, title: '注册' },
  },
  {
    path: '/',
    name: 'dashboard',
    component: HomeView,
    meta: { title: '工作台' },
  },
  {
    path: '/applications',
    name: 'applications',
    component: () => import('../views/ApplicationsView.vue'),
    meta: { title: '我的申请' },
  },
  {
    path: '/applications/new',
    name: 'application-new',
    component: () => import('../views/ApplicationNewView.vue'),
    meta: { title: '新建申请', userOnly: true },
  },
  {
    path: '/applications/:id',
    name: 'application-detail',
    component: () => import('../views/ApplicationDetailView.vue'),
    props: true,
    meta: { title: '申请详情' },
  },
  {
    path: '/certificates',
    name: 'certificates',
    component: () => import('../views/CertificatesView.vue'),
    meta: { title: '我的证书' },
  },
  {
    path: '/certificates/:serial',
    name: 'certificate-detail',
    component: () => import('../views/CertificateDetailView.vue'),
    props: true,
    meta: { title: '证书详情' },
  },
  {
    path: '/verify',
    name: 'verify',
    component: () => import('../views/VerifyView.vue'),
    meta: { title: '证书验证' },
  },
  {
    path: '/admin/applications',
    name: 'admin-applications',
    component: () => import('../views/AdminApplicationsView.vue'),
    meta: { title: '审核管理', requiresAdmin: true },
  },
  {
    path: '/admin/certificates',
    name: 'admin-certificates',
    component: () => import('../views/AdminCertificatesView.vue'),
    meta: { title: '证书管理', requiresAdmin: true },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: ModuleView,
    props: { title: '页面不存在', description: '请通过左侧导航返回已有页面。' },
    meta: { title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  // 首次进入或刷新页面时，向后端确认会话是否仍然有效
  await ensureSession()

  if (to.meta.public) {
    // 已登录用户访问登录页 / 注册页时直接进入工作台
    if (sessionState.user && to.name !== 'not-found') return { name: 'dashboard' }
    return true
  }

  if (!sessionState.user) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta.requiresAdmin && !isAdmin()) {
    return { name: 'dashboard', query: { denied: 'admin' } }
  }

  if (to.meta.userOnly && isAdmin()) {
    return { name: 'admin-applications' }
  }

  return true
})

router.afterEach((to) => {
  const title = to.meta?.title
  document.title = title ? `${title} · CA证书服务中心` : 'CA证书服务中心'
})

export default router
