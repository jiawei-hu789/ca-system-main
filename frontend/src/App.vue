<script setup>
const links = [
  { to: '/', label: '服务概览' },
  { to: '/applications/new', label: '认证申请' },
  { to: '/certificates', label: '我的证书' },
  { to: '/admin/applications', label: '审核管理' },
  { to: '/verify', label: '证书验证' },
]
</script>

<template>
  <div class="app-shell">
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
          :class="{ 'nav-home': link.to === '/' }"
        >{{ link.label }}</RouterLink>
      </nav>
      <p class="sidebar-note">信息安全专业课程设计<br />开发骨架 · 业务功能待接入</p>
    </aside>
    <div class="main-shell">
      <header class="topbar">
        <span>身份认证与证书管理</span>
        <span class="environment"><i aria-hidden="true"></i>开发环境</span>
      </header>
      <main id="main-content" class="content">
        <RouterView />
      </main>
    </div>
  </div>
</template>
