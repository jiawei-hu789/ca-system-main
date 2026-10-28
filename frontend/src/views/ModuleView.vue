<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, required: true },
})
</script>

<template>
  <section>
    <p class="eyebrow">CA SYSTEM / 功能模块</p>
    <h1>{{ title }}</h1>
    <div class="module-placeholder">
      <span class="module-tag">待开发</span>
      <h2>页面入口已准备</h2>
      <p>{{ description }}</p>
      <RouterLink to="/" class="text-link">返回服务概览 →</RouterLink>
    </div>
  </section>
</template>
