<script setup>
import { computed, ref } from 'vue'
import { ElButton } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import { getHealth } from '../api/health'

const connection = ref('idle')
const checking = ref(false)
const labels = { idle: '尚未检查', up: '后端已连接', down: '暂时无法连接后端' }
const connectionLabel = computed(() => labels[connection.value])

async function checkConnection() {
  checking.value = true
  try {
    await getHealth()
    connection.value = 'up'
  } catch {
    connection.value = 'down'
  } finally {
    checking.value = false
  }
}

const steps = [
  ['01', '生成密钥', '用户在本地生成密钥对，妥善保存私钥。'],
  ['02', '提交申请', '填写用户信息，提交公钥与证明材料。'],
  ['03', '审核签发', '管理员审核资料，通过后签发证书。'],
  ['04', '管理证书', '查询、下载、验证证书，按需申请吊销。'],
]
</script>

<template>
  <section>
    <p class="eyebrow">CA SYSTEM / 课程设计</p>
    <h1>从身份申请，到可信证书。</h1>
    <p class="lead">在同一流程中完成认证申请、材料审核与证书管理。</p>
    <div class="development-notice">
      <span class="notice-mark" aria-hidden="true">i</span>
      <p>当前为项目基础骨架。导航页面已建立，申请、审核、密钥与证书功能将在后续开发中接入。</p>
    </div>

    <div class="section-heading"><h2>服务流程</h2><span>计划实现</span></div>
    <ol class="process-grid">
      <li v-for="[number, title, description] in steps" :key="number">
        <span class="step-number">{{ number }}</span>
        <h3>{{ title }}</h3>
        <p>{{ description }}</p>
      </li>
    </ol>

    <section class="connection-panel" aria-labelledby="connection-title">
      <div>
        <p class="eyebrow">开发联调</p>
        <h2 id="connection-title">服务连接</h2>
        <p :class="['connection-state', connection]" role="status" aria-live="polite">
          {{ connectionLabel }}
        </p>
        <p v-if="connection === 'down'" class="connection-help">请确认后端已在8080端口启动，再重新检查。</p>
      </div>
      <ElButton type="primary" :loading="checking" @click="checkConnection">检查连接</ElButton>
    </section>
  </section>
</template>
