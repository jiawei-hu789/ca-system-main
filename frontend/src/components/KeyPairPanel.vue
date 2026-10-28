<script setup>
import { computed, ref } from 'vue'
import { ElAlert, ElButton, ElInput, ElMessage, ElTag } from 'element-plus'
import {
  CryptoUnavailableError,
  encryptPrivateKey,
  generateKeyPair,
  isCryptoAvailable,
} from '../utils/crypto'
import { saveBlob } from '../utils/download'

const props = defineProps({
  /** 附加到私钥文件中的申请信息 */
  applicantName: { type: String, default: '' },
  email: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:publicKeyPem'])

const available = isCryptoAvailable()
const keyPair = ref(null)
const publicKeyPem = ref('')
const generating = ref(false)
const saved = ref(false)
const passphrase = ref('')
const confirmPassphrase = ref('')
const saving = ref(false)

const passphraseError = computed(() => {
  if (!passphrase.value) return ''
  if (passphrase.value.length < 8) return '口令至少需要 8 个字符'
  if (confirmPassphrase.value && confirmPassphrase.value !== passphrase.value) return '两次输入的口令不一致'
  return ''
})

const canSavePrivateKey = computed(
  () =>
    Boolean(keyPair.value) &&
    passphrase.value.length >= 8 &&
    passphrase.value === confirmPassphrase.value,
)

async function handleGenerate() {
  generating.value = true
  try {
    const result = await generateKeyPair()
    keyPair.value = result.keyPair
    publicKeyPem.value = result.publicKeyPem
    saved.value = false
    emit('update:publicKeyPem', result.publicKeyPem)
    ElMessage.success('密钥对已生成，请务必加密保存私钥')
  } catch (error) {
    if (error instanceof CryptoUnavailableError) ElMessage.error(error.message)
    else ElMessage.error('密钥生成失败，请重试')
  } finally {
    generating.value = false
  }
}

async function handleSavePrivateKey() {
  if (!canSavePrivateKey.value) return
  saving.value = true
  try {
    const backup = await encryptPrivateKey(keyPair.value, passphrase.value, {
      applicantName: props.applicantName,
      email: props.email,
    })
    const filename = `private-key-${backup.createdAt.replace(/[:.]/g, '-')}.json`
    saveBlob(new Blob([JSON.stringify(backup, null, 2)], { type: 'application/json' }), filename)
    saved.value = true
    ElMessage.success('私钥已加密保存，请与口令分开保管')
  } catch (error) {
    ElMessage.error(error.message || '私钥保存失败')
  } finally {
    saving.value = false
  }
}

async function copyPublicKey() {
  try {
    await navigator.clipboard.writeText(publicKeyPem.value)
    ElMessage.success('公钥已复制')
  } catch {
    ElMessage.warning('浏览器不允许自动复制，请手动选择公钥文本')
  }
}

function reset() {
  keyPair.value = null
  publicKeyPem.value = ''
  saved.value = false
  passphrase.value = ''
  confirmPassphrase.value = ''
  emit('update:publicKeyPem', '')
}

defineExpose({ reset, hasKeys: () => Boolean(keyPair.value) })
</script>

<template>
  <div class="key-panel">
    <ElAlert
      v-if="!available"
      type="error"
      :closable="false"
      show-icon
      title="当前环境无法使用 Web Crypto"
      description="浏览器只在 localhost 或 HTTPS 下开放密钥接口。请改用 http://localhost:5173 访问，或为开发服务器配置 HTTPS。"
    />

    <template v-else>
      <div class="key-panel__row">
        <ElButton type="primary" :loading="generating" :disabled="props.disabled" @click="handleGenerate">
          {{ keyPair ? '重新生成密钥对' : '生成 RSA-2048 密钥对' }}
        </ElButton>
        <ElTag v-if="publicKeyPem" type="success" effect="light">公钥已生成</ElTag>
        <ElTag v-if="saved" type="success" effect="plain">私钥已加密保存</ElTag>
      </div>

      <p class="key-panel__hint">
        密钥对只在浏览器本地生成，私钥不会发送到后端。请先生成密钥，再用口令加密保存私钥。
      </p>

      <template v-if="publicKeyPem">
        <label class="key-panel__label" for="public-key">公钥（SPKI PEM，将随申请提交）</label>
        <ElInput
          id="public-key"
          v-model="publicKeyPem"
          type="textarea"
          :rows="7"
          readonly
          resize="vertical"
          class="key-panel__pem"
        />
        <div class="key-panel__row">
          <ElButton size="small" @click="copyPublicKey">复制公钥</ElButton>
        </div>

        <div class="key-panel__divider"></div>

        <label class="key-panel__label" for="passphrase">私钥保护口令（至少 8 个字符）</label>
        <ElInput
          id="passphrase"
          v-model="passphrase"
          type="password"
          show-password
          autocomplete="new-password"
          placeholder="用于加密私钥文件，请与登录密码区分"
        />
        <label class="key-panel__label" for="passphrase2">确认口令</label>
        <ElInput
          id="passphrase2"
          v-model="confirmPassphrase"
          type="password"
          show-password
          autocomplete="new-password"
          placeholder="再次输入口令"
        />
        <p v-if="passphraseError" class="key-panel__error">{{ passphraseError }}</p>

        <div class="key-panel__row">
          <ElButton type="primary" plain :loading="saving" :disabled="!canSavePrivateKey" @click="handleSavePrivateKey">
            加密并下载私钥文件
          </ElButton>
        </div>
        <p class="key-panel__hint key-panel__hint--warn">
          私钥文件与口令请分开保存。丢失私钥或口令将无法恢复，本系统不保存任何私钥。
        </p>
      </template>
    </template>
  </div>
</template>

<style scoped>
.key-panel { display: grid; gap: 12px; }
.key-panel__row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.key-panel__hint { margin: 0; color: var(--muted); font-size: 12px; line-height: 1.8; }
.key-panel__hint--warn { color: #8a5a3b; }
.key-panel__label { font-size: 12px; color: var(--muted); margin-top: 4px; }
.key-panel__pem :deep(textarea) { font-family: Consolas, Monaco, monospace; font-size: 12px; }
.key-panel__divider { height: 1px; background: var(--border); margin: 6px 0; }
.key-panel__error { margin: 0; color: #a34938; font-size: 12px; }
</style>
