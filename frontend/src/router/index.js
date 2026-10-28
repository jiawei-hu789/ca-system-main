import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

const ModuleView = () => import('../views/ModuleView.vue')

export default createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    {
      path: '/applications/new',
      name: 'application-new',
      component: ModuleView,
      props: {
        title: '认证申请',
        description: '此处将提供姓名、邮箱、公钥和证明材料提交，以及浏览器端密钥生成与私钥加密保存。',
      },
    },
    {
      path: '/certificates',
      name: 'certificates',
      component: ModuleView,
      props: {
        title: '我的证书',
        description: '此处将展示本人申请和证书，支持查看审核结果、下载证书及申请吊销。',
      },
    },
    {
      path: '/admin/applications',
      name: 'admin-applications',
      component: ModuleView,
      props: {
        title: '审核管理',
        description: '此处将提供管理员审核、证明材料查看、证书签发和吊销管理。访问权限将在登录模块中接入。',
      },
    },
    {
      path: '/verify',
      name: 'verify',
      component: ModuleView,
      props: {
        title: '证书验证',
        description: '此处将分别展示CA签名、有效期和系统内吊销状态的检查结果。',
      },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: ModuleView,
      props: { title: '页面不存在', description: '请通过导航返回已有页面。' },
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})
