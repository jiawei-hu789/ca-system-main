<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElUpload,
} from 'element-plus'
import { createApplication } from '../api/applications'
import { formatBytes } from '../utils/download'
import KeyPairPanel from '../components/KeyPairPanel.vue'

const router = useRouter()

const formRef = ref(null)
const keyPanel = ref(null)
const submitting = ref(false)
const publicKeyPem = ref('')

const form = reactive({
  applicantName: '',
  email: '',
  evidenceFiles: [],
})

const MAX_FILES = 3
const MAX_SIZE = 5 * 1024 * 1024
const ALLOWED_EXTENSIONS = ['pdf', 'png', 'jpg', 'jpeg']

const rules = {
  applicantName: [
    { required: true, message: '请输入申请人姓名', trigger: 'blur' },
    { min: 1, max: 100, message: '姓名长度为 1-100 个字符', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
    { max: 254, message: '邮箱最多 254 个字符', trigger: 'blur' },
  ],
}

const evidenceCount = computed(() => form.evidenceFiles.length)
const canSubmit = computed(
  () => Boolean(publicKeyPem.value) && evidenceCount.value >= 1 && evidenceCount.value <= MAX_FILES,
)

function extensionOf(name) {
  const index = String(name).lastIndexOf('.')
  return index === -1 ? '' : String(name).slice(index + 1).toLowerCase()
}

/**
 * 选择文件后在前端先校验：数量、大小、扩展名。
 * 后端还会检查扩展名与文件头，这里只做即时反馈，减少无效请求。
 * 注意 ElUpload 的 before-upload 在 auto-upload=false 时不会触发，所以用 on-change。
 */
function handleFileChange(uploadFile) {
  const raw = uploadFile?.raw
  if (!raw) return

  if (form.evidenceFiles.length >= MAX_FILES) {
    ElMessage.warning(`最多上传 ${MAX_FILES} 份证明材料`)
    return
  }
  if (!ALLOWED_EXTENSIONS.includes(extensionOf(raw.name))) {
    ElMessage.error('证明材料只支持 PDF、PNG 或 JPEG')
    return
  }
  if (raw.size <= 0 || raw.size > MAX_SIZE) {
    ElMessage.error('每份材料需大于 0 且不超过 5MB')
    return
  }
  if (raw.name.length > 128) {
    ElMessage.error('文件名不能超过 128 个字符')
    return
  }
  if (form.evidenceFiles.some((item) => item.name === raw.name && item.size === raw.size)) {
    ElMessage.warning('该文件已选择')
    return
  }
  form.evidenceFiles = [...form.evidenceFiles, raw]
}

function handleRemove(index) {
  form.evidenceFiles = form.evidenceFiles.filter((_item, i) => i !== index)
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (!publicKeyPem.value) {
    ElMessage.warning('请先生成密钥对')
    return
  }
  if (!evidenceCount.value) {
    ElMessage.warning('请至少上传 1 份证明材料')
    return
  }

  submitting.value = true
  try {
    const created = await createApplication({
      applicantName: form.applicantName.trim(),
      email: form.email.trim(),
      publicKeyPem: publicKeyPem.value,
      evidenceFiles: form.evidenceFiles,
    })
    ElMessage.success('申请已提交，请等待管理员审核')
    router.replace({ name: 'application-detail', params: { id: created.id } })
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section>
    <p class="eyebrow">CA SYSTEM / 新建申请</p>
    <h1>提交认证申请</h1>
    <p class="lead">填写申请人信息、生成密钥对并上传证明材料。审核通过后由 CA 签发 X.509 v3 证书。</p>

    <ElAlert
      type="info"
      :closable="false"
      show-icon
      class="step-alert"
      title="私钥不会离开浏览器"
      description="本系统只接收公钥。私钥在浏览器本地加密后由你自行保存，丢失后无法恢复，也不会影响已签发证书的使用。"
    />

    <div class="form-section">
      <h2>一、申请人信息</h2>
      <ElForm ref="formRef" :model="form" :rules="rules" label-position="top" class="apply-form">
        <ElFormItem label="申请人姓名" prop="applicantName">
          <ElInput v-model="form.applicantName" placeholder="将写入证书的 CN 字段，1-100 个字符" maxlength="100" show-word-limit />
        </ElFormItem>
        <ElFormItem label="联系邮箱" prop="email">
          <ElInput v-model="form.email" placeholder="example@example.com" maxlength="254" />
        </ElFormItem>
      </ElForm>
    </div>

    <div class="form-section">
      <h2>二、生成密钥对</h2>
      <KeyPairPanel
        ref="keyPanel"
        :applicant-name="form.applicantName"
        :email="form.email"
        :disabled="submitting"
        @update:public-key-pem="publicKeyPem = $event"
      />
    </div>

    <div class="form-section">
      <h2>三、证明材料</h2>
      <p class="section-hint">支持 PDF、PNG、JPEG，1-3 份，每份不超过 5MB，文件名不超过 128 个字符。</p>
      <ElUpload
        :auto-upload="false"
        :show-file-list="false"
        multiple
        :on-change="handleFileChange"
        accept=".pdf,.png,.jpg,.jpeg"
        class="upload-block"
      >
        <ElButton :disabled="evidenceCount >= MAX_FILES">选择证明材料</ElButton>
        <template #tip>
          <div class="upload-tip">已选择 {{ evidenceCount }} / {{ MAX_FILES }} 份</div>
        </template>
      </ElUpload>

      <ul v-if="evidenceCount" class="file-list">
        <li v-for="(file, index) in form.evidenceFiles" :key="`${file.name}-${index}`">
          <span class="file-name">{{ file.name }}</span>
          <span class="file-size">{{ formatBytes(file.size) }}</span>
          <ElButton size="small" text type="danger" @click="handleRemove(index)">移除</ElButton>
        </li>
      </ul>
    </div>

    <div class="submit-bar">
      <ElButton @click="router.push('/applications')">取消</ElButton>
      <ElButton type="primary" :loading="submitting" :disabled="!canSubmit" @click="handleSubmit">
        提交申请
      </ElButton>
    </div>
    <p v-if="!canSubmit" class="submit-hint">需要先完成「生成密钥对」并选择至少 1 份证明材料。</p>
  </section>
</template>

<style scoped>
.step-alert { margin: 28px 0 8px; }
.form-section { margin-top: 30px; padding: 26px 28px; border: 1px solid var(--border); background: #fffefa; }
.form-section h2 { margin-bottom: 18px; }
.apply-form { max-width: 620px; }
.section-hint { margin: -8px 0 16px; color: var(--muted); font-size: 12px; line-height: 1.8; }
.upload-block { max-width: 620px; }
.upload-tip { color: var(--muted); font-size: 12px; margin-top: 8px; }
.file-list { list-style: none; margin: 14px 0 0; padding: 0; display: grid; gap: 8px; max-width: 620px; }
.file-list li {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid var(--border);
  background: #f8faf5;
}
.file-name { flex: 1; min-width: 0; font-size: 13px; word-break: break-all; }
.file-size { color: var(--muted); font-size: 11px; white-space: nowrap; }
.submit-bar { display: flex; gap: 12px; margin-top: 26px; }
.submit-hint { margin: 12px 0 0; color: #8a5a3b; font-size: 12px; }
</style>
