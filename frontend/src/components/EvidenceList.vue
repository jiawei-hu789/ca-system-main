<script setup>
import { ref } from 'vue'
import { ElButton, ElDialog, ElMessage } from 'element-plus'
import { downloadEvidence } from '../api/applications'
import { formatBytes, previewUrl, revokePreviewUrl, saveBlob } from '../utils/download'

const props = defineProps({
  files: { type: Array, default: () => [] },
})

const previewing = ref(false)
const preview = ref({ url: '', name: '', type: '' })
const busyId = ref('')

function isPreviewable(file) {
  return file.contentType === 'application/pdf' || String(file.contentType).startsWith('image/')
}

async function fetchFile(file) {
  busyId.value = file.id
  try {
    const { blob, filename } = await downloadEvidence(file.id, file.filename)
    return { blob, filename: filename || file.filename }
  } finally {
    busyId.value = ''
  }
}

async function handleDownload(file) {
  try {
    const { blob, filename } = await fetchFile(file)
    saveBlob(blob, filename)
  } catch (error) {
    ElMessage.error(error.message || '材料下载失败')
  }
}

async function handlePreview(file) {
  try {
    const { blob } = await fetchFile(file)
    preview.value = { url: previewUrl(blob), name: file.filename, type: file.contentType }
    previewing.value = true
  } catch (error) {
    ElMessage.error(error.message || '材料预览失败')
  }
}

function closePreview() {
  previewing.value = false
  revokePreviewUrl(preview.value.url)
  preview.value = { url: '', name: '', type: '' }
}

defineExpose({ closePreview })
</script>

<template>
  <div class="evidence-list">
    <p v-if="!props.files.length" class="evidence-empty">没有证明材料</p>
    <div v-for="file in props.files" :key="file.id" class="evidence-item">
      <div class="evidence-meta">
        <span class="evidence-name">{{ file.filename }}</span>
        <span class="evidence-sub">{{ file.contentType }} · {{ formatBytes(file.sizeBytes) }}</span>
      </div>
      <div class="evidence-actions">
        <ElButton
          v-if="isPreviewable(file)"
          size="small"
          :loading="busyId === file.id"
          @click="handlePreview(file)"
        >预览</ElButton>
        <ElButton size="small" :loading="busyId === file.id" @click="handleDownload(file)">下载</ElButton>
      </div>
    </div>

    <ElDialog
      v-model="previewing"
      :title="preview.name"
      width="min(860px, 92vw)"
      top="6vh"
      destroy-on-close
      @closed="closePreview"
    >
      <img v-if="preview.type?.startsWith('image/')" :src="preview.url" class="preview-image" :alt="preview.name" />
      <iframe v-else :src="preview.url" class="preview-frame" :title="preview.name"></iframe>
    </ElDialog>
  </div>
</template>

<style scoped>
.evidence-list { display: grid; gap: 10px; }
.evidence-empty { margin: 0; color: var(--muted); font-size: 13px; }
.evidence-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 16px;
  border: 1px solid var(--border);
  background: #fffefa;
  flex-wrap: wrap;
}
.evidence-meta { display: grid; gap: 4px; min-width: 0; }
.evidence-name { font-size: 13px; word-break: break-all; }
.evidence-sub { color: var(--muted); font-size: 11px; }
.evidence-actions { display: flex; gap: 8px; }
.preview-image { display: block; width: 100%; height: auto; }
.preview-frame { width: 100%; height: 70vh; border: 0; }
</style>
