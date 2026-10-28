<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElInput,
  ElMessage,
  ElSkeleton,
} from 'element-plus'
import {
  downloadCertificate,
  getCertificate,
  requestRevocation,
} from '../api/certificates'
import { saveBlob } from '../utils/download'
import { formatDateTime } from '../utils/format'
import StatusTag from '../components/StatusTag.vue'

const props = defineProps({ serial: { type: String, required: true } })

const loading = ref(true)
const certificate = ref(null)
const downloading = ref(false)

const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({ reason: '' })

const canRequestRevocation = computed(
  () => certificate.value?.status === 'VALID' && !pendingRequest.value,
)

const pendingRequest = computed(
  () => (certificate.value?.revocationRequests || []).find((item) => item.status === 'PENDING') || null,
)

const reasonError = computed(() => {
  if (!form.reason) return ''
  const length = form.reason.trim().length
  if (length < 1) return '请填写吊销原因'
  if (length > 500) return '吊销原因最多 500 个字符'
  return ''
})

async function load() {
  loading.value = true
  try {
    certificate.value = await getCertificate(props.serial)
  } catch {
    certificate.value = null
  } finally {
    loading.value = false
  }
}

async function handleDownload() {
  downloading.value = true
  try {
    const { blob, filename } = await downloadCertificate(props.serial)
    saveBlob(blob, filename)
    ElMessage.success('证书已下载')
  } catch {
    // 拦截器已提示
  } finally {
    downloading.value = false
  }
}

function openDialog() {
  form.reason = ''
  dialogVisible.value = true
}

async function submitRevocation() {
  if (!form.reason.trim()) {
    ElMessage.warning('请填写吊销原因')
    return
  }
  if (form.reason.trim().length > 500) {
    ElMessage.warning('吊销原因最多 500 个字符')
    return
  }
  submitting.value = true
  try {
    await requestRevocation(props.serial, form.reason.trim())
    ElMessage.success('吊销申请已提交，等待管理员处理')
    dialogVisible.value = false
    await load()
  } catch {
    // 拦截器已提示（同一证书重复申请返回 409）
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <section>
    <p class="eyebrow">CA SYSTEM / 证书详情</p>
    <h1>证书详情</h1>

    <div class="toolbar">
      <ElButton @click="$router.back()">返回</ElButton>
      <ElButton :loading="loading" @click="load">刷新</ElButton>
      <ElButton type="primary" :loading="downloading" :disabled="!certificate" @click="handleDownload">
        下载 PEM 证书
      </ElButton>
      <ElButton :disabled="!canRequestRevocation" @click="openDialog">申请吊销</ElButton>
    </div>

    <ElSkeleton v-if="loading" :rows="8" animated />

    <p v-else-if="!certificate" class="empty-state">未找到该证书，或你没有查看权限。</p>

    <template v-else>
      <div class="panel">
        <div class="panel-head">
          <h2>证书信息</h2>
          <StatusTag :status="certificate.status" kind="certificate" />
        </div>
        <ElDescriptions :column="2" border>
          <ElDescriptionsItem label="证书序列号" :span="2">
            <code class="mono">{{ certificate.serial }}</code>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="签发时间">{{ formatDateTime(certificate.issuedAt) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="关联申请">
            <RouterLink :to="`/applications/${certificate.applicationId}`" class="link">
              #{{ certificate.applicationId }}
            </RouterLink>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="生效时间">{{ formatDateTime(certificate.notBefore) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="到期时间">{{ formatDateTime(certificate.notAfter) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="吊销时间">{{ formatDateTime(certificate.revokedAt) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="吊销原因">{{ certificate.revocationReason || '—' }}</ElDescriptionsItem>
        </ElDescriptions>
      </div>

      <div class="panel">
        <div class="panel-head"><h2>吊销申请记录</h2></div>
        <ElAlert
          v-if="canRequestRevocation"
          type="info"
          :closable="false"
          show-icon
          title="关于吊销"
          description="吊销表示验证方不应继续信任该证书。吊销不会修改已下载的证书文件，也不会销毁私钥；系统内验证会查询吊销记录。"
          class="panel-alert"
        />
        <p v-if="!certificate.revocationRequests?.length" class="panel-empty">暂无吊销申请记录。</p>
        <table v-else class="data-table">
          <thead>
            <tr>
              <th>申请时间</th>
              <th>原因</th>
              <th>状态</th>
              <th>处理时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in certificate.revocationRequests" :key="item.id">
              <td class="mono">{{ formatDateTime(item.createdAt) }}</td>
              <td>{{ item.reason }}</td>
              <td><StatusTag :status="item.status" kind="revocation" size="small" /></td>
              <td class="mono">{{ formatDateTime(item.completedAt) }}</td>
            </tr>
          </tbody>
        </table>
        <p v-if="pendingRequest" class="panel-hint">已有一条待处理的吊销申请，请等待管理员处理。</p>
        <p v-if="certificate.status === 'REVOKED'" class="panel-hint">该证书已被吊销，无法再次提交吊销申请。</p>
      </div>
    </template>

    <ElDialog v-model="dialogVisible" title="提交吊销申请" width="min(520px, 92vw)" destroy-on-close>
      <p class="dialog-hint">请说明吊销原因，管理员审核后会执行吊销。原因最多 500 个字符。</p>
      <ElInput
        v-model="form.reason"
        type="textarea"
        :rows="4"
        maxlength="500"
        show-word-limit
        placeholder="例如：私钥可能已泄露"
      />
      <p v-if="reasonError" class="dialog-error">{{ reasonError }}</p>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="submitting" @click="submitRevocation">提交申请</ElButton>
      </template>
    </ElDialog>
  </section>
</template>

<style scoped>
.toolbar { display: flex; gap: 10px; flex-wrap: wrap; margin: 26px 0 20px; }
.panel { padding: 22px 24px; border: 1px solid var(--border); background: #fffefa; margin-bottom: 20px; }
.panel-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.panel-alert { margin-bottom: 16px; }
.panel-empty { margin: 0; color: var(--muted); font-size: 13px; }
.panel-hint { margin: 12px 0 0; color: #8a5a3b; font-size: 12px; }
.empty-state { padding: 32px; border: 1px dashed var(--border); color: var(--muted); font-size: 13px; }
.data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.data-table th {
  text-align: left;
  padding: 12px 10px;
  color: var(--muted);
  font-weight: 500;
  font-size: 12px;
  border-bottom: 1px solid var(--border);
}
.data-table td { padding: 12px 10px; border-bottom: 1px solid #eef1ea; }
.data-table tbody tr:last-child td { border-bottom: 0; }
.mono { font-family: Consolas, Monaco, monospace; font-size: 12px; word-break: break-all; }
.link { color: #216454; }
.link:hover { text-decoration: underline; }
.dialog-hint { margin: 0 0 12px; color: var(--muted); font-size: 12px; line-height: 1.8; }
.dialog-error { margin: 8px 0 0; color: #a34938; font-size: 12px; }
</style>
