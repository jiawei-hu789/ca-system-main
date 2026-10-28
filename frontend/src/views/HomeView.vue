<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElMessage, ElSkeleton } from 'element-plus'
import { fetchHealth } from '../api/auth'
import { listMyApplications } from '../api/applications'
import { listMyCertificates, downloadRootCertificate } from '../api/certificates'
import { saveBlob } from '../utils/download'
import { formatDateTime } from '../utils/format'
import { isAdmin, sessionState } from '../utils/session'
import StatusTag from '../components/StatusTag.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const health = ref('idle')
const applications = ref([])
const certificates = ref([])
const downloadingRoot = ref(false)

const user = computed(() => sessionState.user)
const admin = computed(() => isAdmin())

const pendingCount = computed(
  () => applications.value.filter((item) => item.status === 'PENDING').length,
)
const validCount = computed(
  () => certificates.value.filter((item) => item.status === 'VALID').length,
)
const latestApplication = computed(() => applications.value[0] || null)

const steps = [
  ['01', '生成密钥', '在浏览器本地生成 RSA 密钥对，私钥加密后自行保存。'],
  ['02', '提交申请', '填写姓名与邮箱，提交公钥和 1-3 份证明材料。'],
  ['03', '审核签发', '管理员核对材料，通过后由 CA 签发 X.509 v3 证书。'],
  ['04', '管理证书', '查询、下载、验证证书，必要时提交吊销申请。'],
]

async function loadData() {
  loading.value = true
  try {
    const tasks = [
      listMyApplications({ limit: 5 }).then((data) => {
        applications.value = data || []
      }),
      listMyCertificates({ limit: 5 }).then((data) => {
        certificates.value = data || []
      }),
      fetchHealth()
        .then(() => {
          health.value = 'up'
        })
        .catch(() => {
          health.value = 'down'
        }),
    ]
    await Promise.allSettled(tasks)
  } finally {
    loading.value = false
  }
}

async function handleRootCertificate() {
  downloadingRoot.value = true
  try {
    const { blob, filename } = await downloadRootCertificate()
    saveBlob(blob, filename)
    ElMessage.success('根 CA 证书已下载')
  } catch {
    // 拦截器已提示
  } finally {
    downloadingRoot.value = false
  }
}

onMounted(() => {
  if (route.query.denied === 'admin') {
    ElMessage.warning('该页面仅管理员可访问')
    router.replace({ name: 'dashboard' })
  }
  loadData()
})
</script>

<template>
  <section>
    <p class="eyebrow">CA SYSTEM / 工作台</p>
    <h1>{{ user?.username }}，欢迎回来。</h1>
    <p class="lead">
      {{ admin ? '你是管理员，可以审核申请、签发与管理证书。' : '在这里提交认证申请，并管理已签发的证书。' }}
    </p>

    <ElSkeleton v-if="loading" :rows="6" animated class="skeleton-block" />

    <template v-else>
      <div class="stat-grid">
        <div class="stat-card">
          <span class="stat-label">我的申请</span>
          <strong class="stat-value">{{ applications.length }}</strong>
          <span class="stat-sub">待审核 {{ pendingCount }} 条</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">我的证书</span>
          <strong class="stat-value">{{ certificates.length }}</strong>
          <span class="stat-sub">有效 {{ validCount }} 张</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">后端服务</span>
          <strong class="stat-value" :class="health === 'down' ? 'is-down' : 'is-up'">
            {{ health === 'up' ? '已连接' : health === 'down' ? '未连接' : '检查中' }}
          </strong>
          <span class="stat-sub">/api/health</span>
        </div>
      </div>

      <div class="dashboard-grid">
        <section class="panel">
          <div class="panel-head">
            <h2>最近申请</h2>
            <RouterLink to="/applications" class="text-link">全部申请 →</RouterLink>
          </div>
          <p v-if="!applications.length" class="panel-empty">
            还没有提交过申请。<RouterLink to="/applications/new" class="text-link">立即新建申请 →</RouterLink>
          </p>
          <ul v-else class="record-list">
            <li v-for="item in applications" :key="item.id">
              <RouterLink :to="`/applications/${item.id}`" class="record-link">
                <span class="record-title">{{ item.applicantName }}</span>
                <span class="record-sub">{{ item.email }} · {{ formatDateTime(item.createdAt) }}</span>
              </RouterLink>
              <StatusTag :status="item.status" kind="application" size="small" />
            </li>
          </ul>
        </section>

        <section class="panel">
          <div class="panel-head">
            <h2>最近证书</h2>
            <RouterLink to="/certificates" class="text-link">全部证书 →</RouterLink>
          </div>
          <p v-if="!certificates.length" class="panel-empty">审核通过后会在这里看到签发的证书。</p>
          <ul v-else class="record-list">
            <li v-for="item in certificates" :key="item.serial">
              <RouterLink :to="`/certificates/${item.serial}`" class="record-link">
                <span class="record-title mono">{{ item.serial }}</span>
                <span class="record-sub">有效期至 {{ formatDateTime(item.notAfter) }}</span>
              </RouterLink>
              <StatusTag :status="item.status" kind="certificate" size="small" />
            </li>
          </ul>
        </section>
      </div>

      <div class="panel">
        <div class="panel-head">
          <h2>快捷操作</h2>
        </div>
        <div class="quick-actions">
          <template v-if="admin">
            <ElButton type="primary" @click="router.push('/admin/applications')">审核申请</ElButton>
            <ElButton @click="router.push('/admin/certificates')">证书管理</ElButton>
          </template>
          <template v-else>
            <ElButton type="primary" @click="router.push('/applications/new')">新建认证申请</ElButton>
            <ElButton :disabled="!latestApplication" @click="router.push('/applications')">查看我的申请</ElButton>
          </template>
          <ElButton :loading="downloadingRoot" @click="handleRootCertificate">下载根 CA 证书</ElButton>
          <ElButton @click="router.push('/verify')">验证证书</ElButton>
        </div>
      </div>

      <div class="section-heading"><h2>服务流程</h2><span>四步完成</span></div>
      <ol class="process-grid">
        <li v-for="[number, title, description] in steps" :key="number">
          <span class="step-number">{{ number }}</span>
          <h3>{{ title }}</h3>
          <p>{{ description }}</p>
        </li>
      </ol>
    </template>
  </section>
</template>

<style scoped>
.skeleton-block { margin-top: 32px; }
.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin: 32px 0 24px;
}
.stat-card {
  display: grid;
  gap: 6px;
  padding: 22px 24px;
  border: 1px solid var(--border);
  background: #fffefa;
}
.stat-label { color: var(--muted); font-size: 12px; }
.stat-value { font-size: 26px; font-weight: 600; color: var(--ink); }
.stat-value.is-up { color: #216454; font-size: 18px; }
.stat-value.is-down { color: #a34938; font-size: 18px; }
.stat-sub { color: var(--muted); font-size: 11px; }
.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}
.panel { padding: 22px 24px; border: 1px solid var(--border); background: #fffefa; margin-bottom: 24px; }
.dashboard-grid .panel { margin-bottom: 0; }
.panel-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.panel-empty { margin: 0; color: var(--muted); font-size: 13px; line-height: 1.9; }
.record-list { list-style: none; margin: 0; padding: 0; display: grid; gap: 2px; }
.record-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--border);
}
.record-list li:last-child { border-bottom: 0; }
.record-link { display: grid; gap: 3px; min-width: 0; }
.record-title { font-size: 13px; word-break: break-all; }
.record-sub { color: var(--muted); font-size: 11px; }
.mono { font-family: Consolas, Monaco, monospace; }
.quick-actions { display: flex; gap: 10px; flex-wrap: wrap; }

@media (max-width: 1100px) {
  .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .dashboard-grid { grid-template-columns: 1fr; }
}
@media (max-width: 700px) {
  .stat-grid { grid-template-columns: 1fr; }
}
</style>
