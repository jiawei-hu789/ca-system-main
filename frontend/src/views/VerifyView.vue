<script setup>
import { computed, ref } from 'vue'
import { ElAlert, ElButton, ElMessage, ElUpload } from 'element-plus'
import { verifyCertificate } from '../api/certificates'
import VerifyResult from '../components/VerifyResult.vue'

const MAX_SIZE = 64 * 1024

const selectedFile = ref(null)
const verifying = ref(false)
const result = ref(null)

const canVerify = computed(() => Boolean(selectedFile.value) && !verifying.value)

function handleFileChange(uploadFile) {
  const raw = uploadFile?.raw
  if (!raw) return
  if (raw.size <= 0) {
    ElMessage.error('文件为空')
    return
  }
  if (raw.size > MAX_SIZE) {
    ElMessage.error('证书文件不能超过 64KB')
    return
  }
  selectedFile.value = raw
  result.value = null
}

function clearFile() {
  selectedFile.value = null
  result.value = null
}

async function handleVerify() {
  if (!selectedFile.value) return
  verifying.value = true
  try {
    result.value = await verifyCertificate(selectedFile.value)
  } catch {
    result.value = null
  } finally {
    verifying.value = false
  }
}
</script>

<template>
  <section>
    <p class="eyebrow">CA SYSTEM / 证书验证</p>
    <h1>验证证书</h1>
    <p class="lead">上传单张 PEM 或 DER 格式的 X.509 证书，系统会检查 CA 签名、有效期、系统内吊销状态以及是否与已签发记录一致。</p>

    <ElAlert
      type="info"
      :closable="false"
      show-icon
      class="step-alert"
      title="验证范围说明"
      description="本版本通过系统内记录检查吊销状态，尚未发布 CRL 或 OCSP。未知证书不会显示为有效。"
    />

    <div class="panel">
      <div class="panel-head"><h2>选择证书文件</h2></div>
      <ElUpload
        :auto-upload="false"
        :show-file-list="false"
        accept=".pem,.crt,.cer,.der"
        :on-change="handleFileChange"
      >
        <ElButton>选择证书文件</ElButton>
        <template #tip>
          <div class="upload-tip">支持 PEM / DER，单张证书，不超过 64KB</div>
        </template>
      </ElUpload>

      <div v-if="selectedFile" class="selected-file">
        <span class="selected-name">{{ selectedFile.name }}</span>
        <span class="selected-size">{{ selectedFile.size }} B</span>
        <ElButton size="small" text @click="clearFile">移除</ElButton>
      </div>

      <div class="panel-actions">
        <ElButton type="primary" :loading="verifying" :disabled="!canVerify" @click="handleVerify">
          开始验证
        </ElButton>
      </div>
    </div>

    <div v-if="result" class="panel">
      <div class="panel-head"><h2>验证结果</h2></div>
      <VerifyResult :result="result" />
    </div>
  </section>
</template>

<style scoped>
.step-alert { margin: 28px 0 20px; }
.panel { padding: 22px 24px; border: 1px solid var(--border); background: #fffefa; margin-bottom: 20px; }
.panel-head { margin-bottom: 16px; }
.panel-actions { display: flex; gap: 10px; margin-top: 18px; }
.upload-tip { color: var(--muted); font-size: 12px; margin-top: 8px; }
.selected-file {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
  padding: 10px 14px;
  border: 1px solid var(--border);
  background: #f8faf5;
  max-width: 620px;
}
.selected-name { flex: 1; min-width: 0; font-size: 13px; word-break: break-all; }
.selected-size { color: var(--muted); font-size: 11px; }
</style>
