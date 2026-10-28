<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElMessage, ElTag } from 'element-plus'
import { logout } from './api/auth'
import { resetSession, sessionState } from './utils/session'

const route = useRoute()
const router = useRouter()

const user = computed(() => sessionState.user)
const isAdmin = computed(() => user.value?.role === 'ADMIN')

// 登录页、注册页不套侧边栏
const bare = computed(() => Boolean(route.meta.bare))

const userLinks = [
  { to: '/', label: '工作台', exact: true },
  { to: '/applications', label: '我的申请' },
  { to: '/applications/new', label: '新建申请' },
  { to: '/certificates', label: '我的证书' },
  { to: '/verify', label: '证书验证' },
]

const adminLinks = [
  { to: '/', label: '工作台', exact: true },
  { to: '/admin/applications', label: '审核管理' },
  { to: '/admin/certificates', label: '证书管理' },
  { to: '/verify', label: '证书验证' },
]

const links = computed(() => (isAdmin.value ? adminLinks : userLinks))

async function handleLogout() {
  try {
    await logout()
  } catch {
    // 会话可能已失效，忽略错误继续清理本地状态
  }
  resetSession()
  ElMessage.success('已退出登录')
  router.replace({ name: 'login' })
}
</script>

<template>
  <RouterView v-if="bare" />

  <div v-else class="app-shell">
    <aside class="sidebar" aria-label="主导航">
      <RouterLink to="/" class="brand" aria-label="CA证书服务中心首页">
        <span class="brand-symbol" aria-hidden="true">CA</span>
        <span>证书服务中心<small>CERTIFICATE AUTHORITY</small></span>
      </RouterLink>

      <nav>
        <RouterLink
          v-for="link in links"
          :key="link.to"
          :to="link.to"
          :class="{ 'nav-home': link.exact }"
        >{{ link.label }}</RouterLink>
      </nav>
    </aside>

    <div class="main-shell">
      <header class="topbar">
        <span class="topbar__title">身份认证与证书管理</span>
        <div v-if="user" class="topbar-user">
          <span class="topbar-user__name">{{ user.username }}</span>
          <ElTag :type="isAdmin ? 'danger' : 'info'" size="small" effect="plain">
            {{ isAdmin ? '管理员' : '普通用户' }}
          </ElTag>
          <ElButton size="small" text class="topbar-user__logout" @click="handleLogout">退出登录</ElButton>
        </div>
      </header>
      <main id="main-content" class="content">
        <RouterView />
      </main>
    </div>
  </div>
</template>
