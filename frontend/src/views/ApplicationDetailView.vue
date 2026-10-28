<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElButton, ElDescriptions, ElDescriptionsItem, ElSkeleton } from 'element-plus'
import { getApplication } from '../api/applications'
import { downloadCertificate, listMyCertificates } from '../api/certificates'
import { saveBlob } from '../utils/download'
import { formatDateTime } from '../utils/format'
import EvidenceList from '../components/EvidenceList.vue'
import StatusTag from '../components/StatusTag.vue'

const props = defineProps({ id: { type: String, required: true } })

const loading = ref(true)
const application = ref(null)
const certificate = ref(null)
const downloading = ref(false)

const isApproved = computed(() => application.value?.status === 'APPROVED')

async function load() {
  loading.value = true
  try {
    application.value = await getApplication(props.id)
    // 审核通过后会签发证书，按申请编号找不到证书接口，
    // 这里退化为在本人证书列表中匹配 applicationId
    if (application.value?.status === 'APPROVED') {
      const list = await listMyCertificates({ limit: 100 })
      certificate.value =
        (list || []).find((item) => String(item.applicationId) === String(application.value.id)) || null
    }
  } catch {
    application.value = null
  } finally {
    loading.value = false
  }
}

async function handleDownload() {
  if (!certificate.value) return
  downloading.value = true
  try {
    const { blob, filename } = await downloadCertificate(certificate.value.serial)
    saveBlob(blob, filename)
  } catch {
    // 拦截器已提示
  } finally {
    downloading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section>
    <p class="eyebrow">CA SYSTEM / 申请详情</p>
    <h1>申请 #{{ props.id }}</h1>

    <div class="toolbar">
      <ElButton @click="$router.back()">返回</ElButton>
      <ElButton :loading="loading" @click="load">刷新</ElButton>
    </div>

    <ElSkeleton v-if="loading" :rows="8" animated />

    <p v-else-if="!application" class="empty-state">未找到该申请，或你没有查看权限。</p>

    <template v-else>
      <div class="panel">
        <div class="panel-head">
          <h2>提交信息</h2>
          <StatusTag :status="application.status" kind="application" />
        </div>
        <ElDescriptions :column="2" border>
          <ElDescriptionsItem label="申请人">{{ application.applicantName }}</ElDescriptionsItem>
          <ElDescriptionsItem label="邮箱">{{ application.email }}</ElDescriptionsItem>
          <ElDescriptionsItem label="提交时间">{{ formatDateTime(application.createdAt) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="审核时间">{{ formatDateTime(application.reviewedAt) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="审核意见" :span="2">
            {{ application.reviewComment || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="公钥（SPKI PEM）" :span="2">
            <pre class="pem-block">{{ application.publicKeyPem }}</pre>
          </ElDescriptionsItem>
        </ElDescriptions>
      </div>

      <div class="panel">
        <div class="panel-head"><h2>证明材料</h2></div>
        <EvidenceList :files="application.evidenceFiles || []" />
      </div>

      <div v-if="isApproved" class="panel">
        <div class="panel-head">
          <h2>签发结果</h2>
          <StatusTag v-if="certificate" :status="certificate.status" kind="certificate" />
        </div>
        <template v-if="certificate">
          <ElDescriptions :column="2" border>
            <ElDescriptionsItem label="证书序列号">
              <code class="mono">{{ certificate.serial }}</code>
            </ElDescriptionsItem>
            <ElDescriptionsItem label="签发时间">{{ formatDateTime(certificate.issuedAt) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="生效时间">{{ formatDateTime(certificate.notBefore) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="到期时间">{{ formatDateTime(certificate.notAfter) }}</ElDescriptionsItem>
          </ElDescriptions>
          <div class="panel-actions">
            <ElButton type="primary" :loading="downloading" @click="handleDownload">下载 PEM 证书</ElButton>
            <ElButton @click="$router.push(`/certificates/${certificate.serial}`)">查看证书详情</ElButton>
          </div>
        </template>
        <p v-else class="panel-empty">证书已签发，但未在本人证书列表中找到对应记录，请到「我的证书」查看。</p>
      </div>

      <div v-else-if="application.status === 'REJECTED'" class="panel panel--rejected">
        <div class="panel-head"><h2>审核结果</h2></div>
        <p class="panel-empty">该申请已被驳回，原因：{{ application.reviewComment || '未填写' }}</p>
        <p class="panel-empty">可以修改信息后重新提交一份新的申请。</p>
        <div class="panel-actions">
          <ElButton type="primary" @click="$router.push('/applications/new')">重新提交申请</ElButton>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.toolbar { display: flex; gap: 10px; margin: 26px 0 20px; }
.panel { padding: 22px 24px; border: 1px solid var(--border); background: #fffefa; margin-bottom: 20px; }
.panel--rejected { border-left: 4px solid #a34938; }
.panel-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.panel-empty { margin: 0 0 12px; color: var(--muted); font-size: 13px; line-height: 1.9; }
.panel-actions { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 16px; }
.empty-state { padding: 32px; border: 1px dashed var(--border); color: var(--muted); font-size: 13px; }
.pem-block {
  margin: 0;
  font-family: Consolas, Monaco, monospace;
  font-size: 11px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 180px;
  overflow: auto;
}
.mono { font-family: Consolas, Monaco, monospace; font-size: 12px; word-break: break-all; }
</style>
