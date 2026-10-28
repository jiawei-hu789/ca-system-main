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
  ElOption,
  ElSelect,
  ElSkeleton,
} from 'element-plus'
import {
  getApplication,
  listAdminApplications,
  reviewApplication,
} from '../api/applications'
import { formatDateTime } from '../utils/format'
import EvidenceList from '../components/EvidenceList.vue'
import ListPager from '../components/ListPager.vue'
import StatusTag from '../components/StatusTag.vue'

const LIMIT = 10

const loading = ref(true)
const items = ref([])
const offset = ref(0)
const statusFilter = ref('PENDING')

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

const reviewVisible = ref(false)
const reviewing = ref(false)
const reviewForm = reactive({ id: null, decision: 'APPROVE', comment: '' })

const commentError = computed(() => {
  if (reviewForm.comment && reviewForm.comment.length > 1000) return '审核意见最多 1000 个字符'
  if (reviewForm.decision === 'REJECT' && !reviewForm.comment.trim()) return '驳回必须填写审核意见'
  return ''
})

const canSubmitReview = computed(() => !commentError.value && !reviewing.value)

const statusOptions = [
  { value: 'PENDING', label: '待审核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: '', label: '全部' },
]

async function load() {
  loading.value = true
  try {
    const data = await listAdminApplications({
      status: statusFilter.value || undefined,
      limit: LIMIT,
      offset: offset.value,
    })
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

function changeStatus() {
  offset.value = 0
  load()
}

async function openDetail(id) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getApplication(id)
  } catch {
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

function openReview(item, decision) {
  reviewForm.id = item.id
  reviewForm.decision = decision
  reviewForm.comment = ''
  reviewVisible.value = true
}

async function submitReview() {
  if (commentError.value) return
  reviewing.value = true
  try {
    await reviewApplication(reviewForm.id, {
      decision: reviewForm.decision,
      comment: reviewForm.comment.trim() || null,
    })
    ElMessage.success(reviewForm.decision === 'APPROVE' ? '审核通过，证书已签发' : '申请已驳回')
    reviewVisible.value = false
    if (detailVisible.value) detailVisible.value = false
    await load()
  } catch (error) {
    // 重复审核返回 409，提示后刷新列表
    if (error.status === 409) await load()
  } finally {
    reviewing.value = false
  }
}

onMounted(load)
</script>

<template>
  <section>
    <p class="eyebrow">CA SYSTEM / 审核管理</p>
    <h1>申请审核</h1>
    <p class="lead">查看申请人提交的信息与证明材料，选择通过（同时签发证书）或驳回（需填写意见）。</p>

    <div class="toolbar">
      <ElSelect v-model="statusFilter" class="status-select" @change="changeStatus">
        <ElOption
          v-for="option in statusOptions"
          :key="option.value || 'all'"
          :label="option.label"
          :value="option.value"
        />
      </ElSelect>
      <ElButton :loading="loading" @click="load">刷新</ElButton>
    </div>

    <ElAlert
      type="warning"
      :closable="false"
      show-icon
      class="review-alert"
      title="审核注意事项"
      description="审核通过会立即签发 X.509 v3 证书，且同一申请不能重复审核。请先下载核对证明材料，再决定通过或驳回。"
    />

    <ElSkeleton v-if="loading && !items.length" :rows="5" animated />

    <p v-else-if="!items.length" class="empty-state">当前筛选条件下没有申请。</p>

    <div v-else class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>编号</th>
            <th>申请人</th>
            <th>邮箱</th>
            <th>材料</th>
            <th>状态</th>
            <th>提交时间</th>
            <th class="col-actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="item.id">
            <td class="mono">#{{ item.id }}</td>
            <td>{{ item.applicantName }}</td>
            <td class="mono">{{ item.email }}</td>
            <td>{{ item.evidenceFiles?.length || 0 }} 份</td>
            <td><StatusTag :status="item.status" kind="application" size="small" /></td>
            <td class="mono">{{ formatDateTime(item.createdAt) }}</td>
            <td class="col-actions">
              <ElButton size="small" text @click="openDetail(item.id)">详情</ElButton>
              <template v-if="item.status === 'PENDING'">
                <ElButton size="small" text type="success" @click="openReview(item, 'APPROVE')">通过</ElButton>
                <ElButton size="small" text type="danger" @click="openReview(item, 'REJECT')">驳回</ElButton>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
      <ListPager
        :offset="offset"
        :limit="LIMIT"
        :count="items.length"
        :loading="loading"
        @change="changePage"
      />
    </div>

    <ElDialog v-model="detailVisible" title="申请详情" width="min(880px, 94vw)" top="5vh" destroy-on-close>
      <ElSkeleton v-if="detailLoading" :rows="6" animated />
      <template v-else-if="detail">
        <ElDescriptions :column="2" border>
          <ElDescriptionsItem label="申请编号">#{{ detail.id }}</ElDescriptionsItem>
          <ElDescriptionsItem label="用户编号">{{ detail.userId }}</ElDescriptionsItem>
          <ElDescriptionsItem label="申请人">{{ detail.applicantName }}</ElDescriptionsItem>
          <ElDescriptionsItem label="邮箱">{{ detail.email }}</ElDescriptionsItem>
          <ElDescriptionsItem label="状态">
            <StatusTag :status="detail.status" kind="application" size="small" />
          </ElDescriptionsItem>
          <ElDescriptionsItem label="提交时间">{{ formatDateTime(detail.createdAt) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="审核时间">{{ formatDateTime(detail.reviewedAt) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="审核意见">{{ detail.reviewComment || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="公钥（SPKI PEM）" :span="2">
            <pre class="pem-block">{{ detail.publicKeyPem }}</pre>
          </ElDescriptionsItem>
        </ElDescriptions>
        <h3 class="dialog-subtitle">证明材料</h3>
        <EvidenceList :files="detail.evidenceFiles || []" />
      </template>
      <template #footer>
        <ElButton @click="detailVisible = false">关闭</ElButton>
        <template v-if="detail?.status === 'PENDING'">
          <ElButton type="danger" plain @click="openReview(detail, 'REJECT')">驳回</ElButton>
          <ElButton type="primary" @click="openReview(detail, 'APPROVE')">审核通过并签发</ElButton>
        </template>
      </template>
    </ElDialog>

    <ElDialog
      v-model="reviewVisible"
      :title="reviewForm.decision === 'APPROVE' ? '审核通过并签发证书' : '驳回申请'"
      width="min(520px, 92vw)"
      destroy-on-close
    >
      <p class="dialog-hint">
        {{
          reviewForm.decision === 'APPROVE'
            ? '通过后将立即签发 X.509 v3 证书，该操作不可撤销。'
            : '驳回必须填写审核意见，申请人可以看到该意见。'
        }}
      </p>
      <ElInput
        v-model="reviewForm.comment"
        type="textarea"
        :rows="4"
        maxlength="1000"
        show-word-limit
        :placeholder="reviewForm.decision === 'APPROVE' ? '审核意见（可选）' : '驳回原因（必填）'"
      />
      <p v-if="commentError" class="dialog-error">{{ commentError }}</p>
      <template #footer>
        <ElButton @click="reviewVisible = false">取消</ElButton>
        <ElButton
          :type="reviewForm.decision === 'APPROVE' ? 'primary' : 'danger'"
          :loading="reviewing"
          :disabled="!canSubmitReview"
          @click="submitReview"
        >确认{{ reviewForm.decision === 'APPROVE' ? '通过' : '驳回' }}</ElButton>
      </template>
    </ElDialog>
  </section>
</template>

<style scoped>
.toolbar { display: flex; gap: 10px; flex-wrap: wrap; margin: 26px 0 16px; align-items: center; }
.status-select { width: 150px; }
.review-alert { margin-bottom: 20px; }
.empty-state { padding: 32px; border: 1px dashed var(--border); color: var(--muted); font-size: 13px; }
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
.col-actions { text-align: right; white-space: nowrap; }
.mono { font-family: Consolas, Monaco, monospace; font-size: 12px; }
.pem-block {
  margin: 0;
  font-family: Consolas, Monaco, monospace;
  font-size: 11px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 160px;
  overflow: auto;
}
.dialog-subtitle { margin: 20px 0 12px; font-size: 14px; }
.dialog-hint { margin: 0 0 12px; color: var(--muted); font-size: 12px; line-height: 1.8; }
.dialog-error { margin: 8px 0 0; color: #a34938; font-size: 12px; }
</style>
