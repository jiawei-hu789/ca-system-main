<script setup>
import { onMounted, ref } from 'vue'
import { ElButton, ElMessage, ElSkeleton } from 'element-plus'
import { listMyApplications } from '../api/applications'
import { formatDateTime } from '../utils/format'
import ListPager from '../components/ListPager.vue'
import StatusTag from '../components/StatusTag.vue'

const LIMIT = 10

const loading = ref(true)
const items = ref([])
const offset = ref(0)

async function load() {
  loading.value = true
  try {
    const data = await listMyApplications({ limit: LIMIT, offset: offset.value })
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

onMounted(load)
</script>

<template>
  <section>
    <p class="eyebrow">CA SYSTEM / 我的申请</p>
    <h1>我的认证申请</h1>
    <p class="lead">查看每一条申请的审核状态与审核意见。审核通过后系统会签发证书。</p>

    <div class="toolbar">
      <ElButton type="primary" @click="$router.push('/applications/new')">新建申请</ElButton>
      <ElButton :loading="loading" @click="load">刷新</ElButton>
    </div>

    <ElSkeleton v-if="loading && !items.length" :rows="5" animated />

    <p v-else-if="!items.length" class="empty-state">
      还没有提交过申请。<RouterLink to="/applications/new" class="text-link">立即新建申请 →</RouterLink>
    </p>

    <div v-else class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>申请人</th>
            <th>邮箱</th>
            <th>状态</th>
            <th>提交时间</th>
            <th>审核时间</th>
            <th class="col-actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="item.id">
            <td>{{ item.applicantName }}</td>
            <td class="mono">{{ item.email }}</td>
            <td><StatusTag :status="item.status" kind="application" size="small" /></td>
            <td class="mono">{{ formatDateTime(item.createdAt) }}</td>
            <td class="mono">{{ formatDateTime(item.reviewedAt) }}</td>
            <td class="col-actions">
              <ElButton size="small" text type="primary" @click="$router.push(`/applications/${item.id}`)">
                详情
              </ElButton>
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
  </section>
</template>

<style scoped>
.toolbar { display: flex; gap: 10px; flex-wrap: wrap; margin: 28px 0 20px; }
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
.data-table td { padding: 14px 10px; border-bottom: 1px solid #eef1ea; vertical-align: middle; }
.data-table tbody tr:last-child td { border-bottom: 0; }
.data-table tbody tr:hover { background: #f8faf5; }
.col-actions { text-align: right; white-space: nowrap; }
.mono { font-family: Consolas, Monaco, monospace; font-size: 12px; }
</style>
