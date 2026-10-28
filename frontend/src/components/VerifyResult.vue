<script setup>
import { ElButton, ElDescriptions, ElDescriptionsItem, ElTag } from 'element-plus'
import StatusTag from './StatusTag.vue'

const props = defineProps({
  result: { type: Object, required: true },
})

function boolTag(value, positive = '通过', negative = '未通过') {
  return { label: value ? positive : negative, type: value ? 'success' : 'danger' }
}
</script>

<template>
  <div class="verify-result">
    <div class="verify-result__verdict" :class="props.result.valid ? 'is-valid' : 'is-invalid'">
      <span class="verify-result__mark">{{ props.result.valid ? '✓' : '✕' }}</span>
      <div>
        <h3>{{ props.result.valid ? '证书验证通过' : '证书验证未通过' }}</h3>
        <p>
          只有本 CA 签名与颁发者匹配、处于有效期内、与系统已签发记录一致且没有吊销记录时，才会判定为通过。
        </p>
      </div>
    </div>

    <ElDescriptions :column="1" border class="verify-result__table">
      <ElDescriptionsItem label="证书序列号">
        <code class="mono">{{ props.result.serial || '—' }}</code>
      </ElDescriptionsItem>
      <ElDescriptionsItem label="CA 签名">
        <ElTag :type="boolTag(props.result.signatureValid).type" effect="light">
          {{ boolTag(props.result.signatureValid).label }}
        </ElTag>
      </ElDescriptionsItem>
      <ElDescriptionsItem label="颁发者可信">
        <ElTag :type="boolTag(props.result.trustedIssuer).type" effect="light">
          {{ boolTag(props.result.trustedIssuer, '属于本系统 CA', '不是本系统 CA').label }}
        </ElTag>
      </ElDescriptionsItem>
      <ElDescriptionsItem label="有效期">
        <ElTag :type="boolTag(props.result.periodValid).type" effect="light">
          {{ boolTag(props.result.periodValid, '在有效期内', '不在有效期内').label }}
        </ElTag>
      </ElDescriptionsItem>
      <ElDescriptionsItem label="吊销状态">
        <StatusTag :status="props.result.revocationStatus" kind="revocationCheck" />
      </ElDescriptionsItem>
      <ElDescriptionsItem label="与系统记录一致">
        <ElTag :type="boolTag(props.result.certificateMatchesRecord).type" effect="light">
          {{ boolTag(props.result.certificateMatchesRecord, '一致', '不一致或未登记').label }}
        </ElTag>
      </ElDescriptionsItem>
      <ElDescriptionsItem label="SHA-256 指纹">
        <code class="mono fingerprint">{{ props.result.fingerprintSha256 || '—' }}</code>
      </ElDescriptionsItem>
    </ElDescriptions>
  </div>
</template>

<style scoped>
.verify-result { display: grid; gap: 20px; }
.verify-result__verdict {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  padding: 20px 24px;
  border: 1px solid var(--border);
  border-left-width: 4px;
  background: #fffefa;
}
.verify-result__verdict.is-valid { border-left-color: #216454; }
.verify-result__verdict.is-invalid { border-left-color: #a34938; }
.verify-result__mark { font-size: 22px; line-height: 1.2; }
.verify-result__verdict.is-valid .verify-result__mark { color: #216454; }
.verify-result__verdict.is-invalid .verify-result__mark { color: #a34938; }
.verify-result__verdict h3 { margin: 0 0 8px; font-size: 16px; }
.verify-result__verdict p { margin: 0; color: var(--muted); font-size: 12px; line-height: 1.8; }
.mono { font-family: Consolas, Monaco, monospace; font-size: 12px; word-break: break-all; }
.fingerprint { font-size: 11px; }
</style>
