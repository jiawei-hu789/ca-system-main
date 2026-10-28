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
  ElTag,
} from 'element-plus'
import {
  downloadCertificate,
  getCertificate,
  listAdminCertificates,
  revokeCertificate,
} from '../api/certificates'
import { saveBlob } from '../utils/download'
import { formatDateTime, shortSerial } from '../utils/format'
import ListPager from '../components/ListPager.vue'
import StatusTag from '../components/StatusTag.vue'

const LIMIT = 10

const loading = ref(true)
const items = ref([])
const offset = ref(0)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

const revokeVisible = ref(false)
const revoking = ref(false)
const revokeForm = reactive({ serial: '', reason: '' })

const statusKeyword = ref('')

/** 后端 /admin/certificates 不支持状态筛选，这里对当前页做本地过滤 */
const filteredItems = computed(() => {
  const keyword = statusKeyword.value.trim()
  if (!keyword) return items.value
  return items.value.filter(
    (item) => item.status === keyword || item.serial.includes(keyword),
  )
})

const pendingRevocations = computed(() =>
  items.value.filter((item) =>
    (item.revocationRequests || []).some((request) => request.status === 'PENDING'),
  ),
)

const reasonError = computed(() => {
  if (!revokeForm.reason) return ''
  const length = revokeForm.reason.trim().length
  if (length < 1) return '请填写吊销原因'
  if (length > 500) return '吊销原因最多 500 个字符'
  return ''
})

const canRevoke = computed(() => !reasonError.value && revokeForm.reason.trim().length > 0 && !revoking.value)

async function load() {
  loading.value = true
  try {
    const data = await listAdminCertificates({ limit: LIMIT, offset: offset.value })
    items.value = data || []
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

function changePage(next) {
  offset.value = next
  load()
}

async function openDetail(serial) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getCertificate(serial)
  } catch {
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

async function handleDownload(serial) {
  try {
    const { blob, filename } = await downloadCertificate(serial)
    saveBlob(blob, filename)
    ElMessage.success('证书已下载')
  } catch {
    // 拦截器已提示
  }
}

function openRevoke(item, reason = '') {
  revokeForm.serial = item.serial
  revokeForm.reason = reason
  revokeVisible.value = true
}

async function submitRevoke() {
  if (!canRevoke.value) return
  revoking.value = true
  try {
    await revokeCertificate(revokeForm.serial, revokeForm.reason.trim())
    ElMessage.success('证书已吊销')
    revokeVisible.value = false
    if (detailVisible.value) detailVisible.value = false
    await load()
  } catch (error) {
    // 已吊销的证书再次吊销返回 409
    if (error.status === 409) await load()
  } finally {
    revoking.value = false
  }
}

function pendingRequestOf(item) {
  return (item.revocationRequests || []).find((request) => request.status === 'PENDING') || null
}

onMounted(load)
</script>

<template>
  <section>
    <p class="eyebrow">CA SYSTEM / 证书管理</p>
    <h1>证书管理</h1>
    <p class="lead">查看全部已签发证书、处理用户提交的吊销申请，并执行吊销。</p>

    <div class="toolbar">
      <ElInput
        v-model="statusKeyword"
        placeholder="按状态或序列号筛选当前页，例如 VALID / REVOKED"
        clearable
        class="filter-input"
      />
      <ElButton :loading="loading" @click="load">刷新</ElButton>
    </div>

    <ElAlert
      v-if="pendingRevocations.length"
      type="warning"
      :closable="false"
      show-icon
      class="pending-alert"
      :title="`当前页有 ${pendingRevocations.length} 张证书存在待处理的吊销申请`"
      description="展开详情可以查看申请原因，确认后执行吊销。吊销后该申请会变为「已完成」。"
    />

    <ElSkeleton v-if="loading && !items.length" :rows="5" animated />

    <p v-else-if="!items.length" class="empty-state">还没有签发过证书。</p>

    <div v-else class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>序列号</th>
            <th>状态</th>
            <th>签发时间</th>
            <th>到期时间</th>
            <th>吊销申请</th>
            <th class="col-actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in filteredItems" :key="item.serial">
            <td>
              <RouterLink :to="`/certificates/${item.serial}`" class="mono link">
                {{ shortSerial(item.serial) }}
              </RouterLink>
            </td>
            <td><StatusTag :status="item.status" kind="certificate" size="small" /></td>
            <td class="mono">{{ formatDateTime(item.issuedAt) }}</td>
            <td class="mono">{{ formatDateTime(item.notAfter) }}</td>
            <td>
              <ElTag v-if="pendingRequestOf(item)" type="warning" size="small" effect="light">待处理</ElTag>
              <span v-else class="muted">—</span>
            </td>
            <td class="col-actions">
              <ElButton size="small" text @click="openDetail(item.serial)">详情</ElButton>
              <ElButton size="small" text type="primary" @click="handleDownload(item.serial)">下载</ElButton>
              <ElButton
                v-if="item.status === 'VALID'"
                size="small"
                text
                type="danger"
                @click="openRevoke(item, pendingRequestOf(item)?.reason || '')"
              >吊销</ElButton>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!filteredItems.length" class="empty-state empty-state--inline">当前页没有匹配的证书。</p>
      <ListPager
        :offset="offset"
        :limit="LIMIT"
        :count="items.length"
        :loading="loading"
        @change="changePage"
      />
    </div>

    <ElDialog v-model="detailVisible" title="证书详情" width="min(760px, 94vw)" destroy-on-close>
      <ElSkeleton v-if="detailLoading" :rows="5" animated />
      <template v-else-if="detail">
        <ElDescriptions :column="2" border>
          <ElDescriptionsItem label="序列号" :span="2">
            <code class="mono">{{ detail.serial }}</code>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="状态">
            <StatusTag :status="detail.status" kind="certificate" size="small" />
          </ElDescriptionsItem>
          <ElDescriptionsItem label="用户编号">{{ detail.userId }}</ElDescriptionsItem>
          <ElDescriptionsItem label="关联申请">
            <RouterLink :to="`/applications/${detail.applicationId}`" class="link">
              #{{ detail.applicationId }}
            </RouterLink>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="签发时间">{{ formatDateTime(detail.issuedAt) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="生效时间">{{ formatDateTime(detail.notBefore) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="到期时间">{{ formatDateTime(detail.notAfter) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="吊销时间">{{ formatDateTime(detail.revokedAt) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="吊销原因">{{ detail.revocationReason || '—' }}</ElDescriptionsItem>
        </ElDescriptions>

        <h3 class="dialog-subtitle">吊销申请记录</h3>
        <p v-if="!detail.revocationRequests?.length" class="muted">暂无吊销申请记录。</p>
        <table v-else class="data-table data-table--nested">
          <thead>
            <tr>
              <th>申请时间</th>
              <th>原因</th>
              <th>状态</th>
              <th>处理时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="request in detail.revocationRequests" :key="request.id">
              <td class="mono">{{ formatDateTime(request.createdAt) }}</td>
              <td>{{ request.reason }}</td>
              <td><StatusTag :status="request.status" kind="revocation" size="small" /></td>
              <td class="mono">{{ formatDateTime(request.completedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </template>
      <template #footer>
        <ElButton @click="detailVisible = false">关闭</ElButton>
        <ElButton
          v-if="detail?.status === 'VALID'"
          type="danger"
          @click="openRevoke(detail, pendingRequestOf(detail)?.reason || '')"
        >执行吊销</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="revokeVisible" title="执行吊销" width="min(520px, 92vw)" destroy-on-close>
      <p class="dialog-hint">
        吊销后证书状态变为「已吊销」，系统内验证将判定为不可信，且该操作不可撤销。
      </p>
      <p class="dialog-serial mono">{{ revokeForm.serial }}</p>
      <ElInput
        v-model="revokeForm.reason"
        type="textarea"
        :rows="4"
        maxlength="500"
        show-word-limit
        placeholder="吊销原因，例如：用户私钥丢失"
      />
      <p v-if="reasonError" class="dialog-error">{{ reasonError }}</p>
      <template #footer>
        <ElButton @click="revokeVisible = false">取消</ElButton>
        <ElButton type="danger" :loading="revoking" :disabled="!canRevoke" @click="submitRevoke">
          确认吊销
        </ElButton>
      </template>
    </ElDialog>
  </section>
</template>

<style scoped>
.toolbar { display: flex; gap: 10px; flex-wrap: wrap; margin: 26px 0 16px; align-items: center; }
.filter-input { max-width: 360px; }
.pending-alert { margin-bottom: 20px; }
.empty-state { padding: 32px; border: 1px dashed var(--border); color: var(--muted); font-size: 13px; }
.empty-state--inline { border: 0; padding: 18px 0 0; }
.table-wrap { border: 1px solid var(--border); background: #fffefa; padding: 8px 18px 18px; overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.data-table th {
  text-align: left;
  padding: 14px 10px;
  color: var(--muted);
  font-weight: 500;
  font-size: 12px;
  border-bottom: 1px solid var(--border);
  white-space: nowrap;
}
.data-table td { padding: 14px 10px; border-bottom: 1px solid #eef1ea; }
.data-table tbody tr:last-child td { border-bottom: 0; }
.data-table tbody tr:hover { background: #f8faf5; }
.data-table--nested { border: 1px solid var(--border); }
.data-table--nested th { padding: 10px; }
.data-table--nested td { padding: 10px; }
.col-actions { text-align: right; white-space: nowrap; }
.mono { font-family: Consolas, Monaco, monospace; font-size: 12px; word-break: break-all; }
.muted { color: var(--muted); font-size: 12px; }
.link { color: #216454; }
.link:hover { text-decoration: underline; }
.dialog-subtitle { margin: 20px 0 12px; font-size: 14px; }
.dialog-hint { margin: 0 0 10px; color: var(--muted); font-size: 12px; line-height: 1.8; }
.dialog-serial { margin: 0 0 12px; color: var(--ink); }
.dialog-error { margin: 8px 0 0; color: #a34938; font-size: 12px; }
</style>
