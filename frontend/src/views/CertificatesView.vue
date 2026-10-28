<script setup>
import { onMounted, ref } from 'vue'
import { ElButton, ElMessage, ElSkeleton } from 'element-plus'
import { downloadCertificate, listMyCertificates } from '../api/certificates'
import { saveBlob } from '../utils/download'
import { formatDateTime, shortSerial } from '../utils/format'
import ListPager from '../components/ListPager.vue'
import StatusTag from '../components/StatusTag.vue'

const LIMIT = 10

const loading = ref(true)
const items = ref([])
const offset = ref(0)
const busySerial = ref('')

async function load() {
  loading.value = true
  try {
    const data = await listMyCertificates({ limit: LIMIT, offset: offset.value })
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

async function handleDownload(serial) {
  busySerial.value = serial
  try {
    const { blob, filename } = await downloadCertificate(serial)
    saveBlob(blob, filename)
    ElMessage.success('证书已下载')
  } catch {
    // 拦截器已提示
  } finally {
    busySerial.value = ''
  }
}

onMounted(load)
</script>

<template>
  <section>
    <p class="eyebrow">CA SYSTEM / 我的证书</p>
    <h1>我的证书</h1>
    <p class="lead">查看已签发的证书、下载 PEM 文件，必要时提交吊销申请。</p>

    <div class="toolbar">
      <ElButton :loading="loading" @click="load">刷新</ElButton>
      <ElButton @click="$router.push('/verify')">验证证书</ElButton>
    </div>

    <ElSkeleton v-if="loading && !items.length" :rows="5" animated />

    <p v-else-if="!items.length" class="empty-state">
      还没有证书。证书会在申请审核通过后由 CA 签发。
      <RouterLink to="/applications/new" class="text-link">新建申请 →</RouterLink>
    </p>

    <div v-else class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>序列号</th>
            <th>状态</th>
            <th>生效时间</th>
            <th>到期时间</th>
            <th class="col-actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="item.serial">
            <td>
              <RouterLink :to="`/certificates/${item.serial}`" class="mono link">
                {{ shortSerial(item.serial) }}
              </RouterLink>
            </td>
            <td><StatusTag :status="item.status" kind="certificate" size="small" /></td>
            <td class="mono">{{ formatDateTime(item.notBefore) }}</td>
            <td class="mono">{{ formatDateTime(item.notAfter) }}</td>
            <td class="col-actions">
              <ElButton
                size="small"
                text
                type="primary"
                :loading="busySerial === item.serial"
                @click="handleDownload(item.serial)"
              >下载</ElButton>
              <ElButton size="small" text @click="$router.push(`/certificates/${item.serial}`)">详情</ElButton>
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
.toolbar { display: flex; gap: 10px; margin: 26px 0 20px; }
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
.link { color: #216454; }
.link:hover { text-decoration: underline; }
</style>
