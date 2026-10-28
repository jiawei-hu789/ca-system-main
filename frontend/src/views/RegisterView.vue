<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElAlert, ElButton, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus'
import { login, register } from '../api/auth'
import { setUser } from '../utils/session'

const router = useRouter()

const formRef = ref(null)
const loading = ref(false)
const form = reactive({ username: '', password: '', confirm: '' })

const USERNAME_PATTERN = /^[A-Za-z0-9._-]{3,32}$/

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!value) return callback()
        if (!USERNAME_PATTERN.test(value)) {
          return callback(new Error('用户名须为 3-32 位字母、数字、点、下划线或连字符'))
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!value) return callback()
        if (value.length < 12) return callback(new Error('密码至少 12 个字符'))
        // 后端按 UTF-8 编码校验不超过 72 字节
        if (new TextEncoder().encode(value).length > 72) {
          return callback(new Error('密码 UTF-8 编码不能超过 72 字节'))
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  confirm: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value && value !== form.password) return callback(new Error('两次输入的密码不一致'))
        callback()
      },
      trigger: 'blur',
    },
  ],
}

const passwordBytes = computed(() => new TextEncoder().encode(form.password).length)

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await register(form.username.trim(), form.password)
    ElMessage.success('注册成功，正在登录…')
    // 注册接口不会建立会话，注册成功后直接登录
    const user = await login(form.username.trim(), form.password)
    setUser(user)
    router.replace({ name: 'dashboard' })
  } catch {
    // 拦截器已给出提示
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

      <p class="eyebrow">CA SYSTEM / 注册</p>
      <h2 class="auth-title">注册普通用户</h2>
      <p class="auth-lead">公开注册只会创建普通用户。管理员账号由后端首次启动时的环境变量创建。</p>

      <ElAlert
        type="info"
        :closable="false"
        show-icon
        title="密码要求"
        description="密码至少 12 个字符，且 UTF-8 编码不超过 72 字节。请勿使用与其他系统相同的密码。"
        class="auth-alert"
      />

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
            autocomplete="new-password"
          />
        </ElFormItem>
        <p class="auth-hint">当前密码 UTF-8 长度：{{ passwordBytes }} / 72 字节</p>
        <ElFormItem label="确认密码" prop="confirm">
          <ElInput
            v-model="form.confirm"
            type="password"
            show-password
            placeholder="再次输入密码"
            autocomplete="new-password"
            @keyup.enter="handleSubmit"
          />
        </ElFormItem>
        <ElButton type="primary" class="auth-submit" :loading="loading" native-type="submit">
          注册并登录
        </ElButton>
      </ElForm>

      <p class="auth-switch">
        已有账号？<RouterLink to="/login" class="text-link">返回登录</RouterLink>
      </p>
    </div>
  </div>
</template>
