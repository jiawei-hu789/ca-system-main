<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus'
import { login } from '../api/auth'
import { setUser } from '../utils/session'

const router = useRouter()
const route = useRoute()

const formRef = ref(null)
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const user = await login(form.username.trim(), form.password)
    setUser(user)
    ElMessage.success(`欢迎回来，${user.username}`)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    if (redirect) router.replace(redirect)
    else router.replace({ name: user.role === 'ADMIN' ? 'admin-applications' : 'dashboard' })
  } catch (error) {
    // 拦截器已提示，这里只在账号密码错误时补充说明
    if (error.status === 401) ElMessage.warning('用户名或密码错误')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-brand">
        <span class="brand-symbol" aria-hidden="true">CA</span>
        <div>
          <h1>证书服务中心</h1>
          <p>CERTIFICATE AUTHORITY</p>
        </div>
      </div>

      <p class="eyebrow">CA SYSTEM / 登录</p>
      <h2 class="auth-title">登录账号</h2>
      <p class="auth-lead">登录后可提交认证申请、查看与下载证书，管理员可进行审核与吊销。</p>

      <ElForm ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleSubmit">
        <ElFormItem label="用户名" prop="username">
          <ElInput v-model="form.username" placeholder="3-32 位字母、数字或 . _ -" autocomplete="username" />
        </ElFormItem>
        <ElFormItem label="密码" prop="password">
          <ElInput
            v-model="form.password"
            type="password"
            show-password
            placeholder="至少 12 个字符"
            autocomplete="current-password"
            @keyup.enter="handleSubmit"
          />
        </ElFormItem>
        <ElButton type="primary" class="auth-submit" :loading="loading" native-type="submit">
          登录
        </ElButton>
      </ElForm>

      <p class="auth-switch">
        还没有账号？<RouterLink to="/register" class="text-link">注册普通用户</RouterLink>
      </p>
    </div>
  </div>
</template>
