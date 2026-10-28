/**
 * 浏览器端密钥处理（Web Crypto API）。
 *
 * 约定：
 * 1. 密钥对只在浏览器本地生成，私钥永不发送到后端。
 * 2. 提交给后端的只有 SPKI 格式的 PEM 公钥（后端校验 RSA 2048-4096 位）。
 * 3. 私钥经 PBKDF2-SHA256 派生的口令密钥做 AES-256-GCM 加密后下载为 JSON 文件，
 *    文件中记录恢复所需的全部算法参数，便于日后解密使用。
 *
 * 注意：Web Crypto 的 crypto.subtle 只在安全上下文可用，
 * 即 localhost / 127.0.0.1 或 HTTPS。通过局域网 IP 访问时需要配置 HTTPS。
 */

const KEY_ALGORITHM = {
  name: 'RSASSA-PKCS1-v1_5',
  modulusLength: 2048,
  publicExponent: new Uint8Array([0x01, 0x00, 0x01]),
  hash: 'SHA-256',
}

const PRIVATE_KEY_FORMAT_VERSION = 1
const KDF_ITERATIONS = 210000
const SALT_BYTES = 16
const IV_BYTES = 12

export class CryptoUnavailableError extends Error {
  constructor(message) {
    super(message)
    this.name = 'CryptoUnavailableError'
  }
}

function requireWebCrypto() {
  if (typeof window === 'undefined' || !window.crypto?.subtle) {
    throw new CryptoUnavailableError(
      '当前环境无法使用 Web Crypto。请通过 localhost 或 HTTPS 访问本系统。',
    )
  }
  return window.crypto.subtle
}

export function isCryptoAvailable() {
  return typeof window !== 'undefined' && Boolean(window.crypto?.subtle)
}

function toBase64(bytes) {
  const view = new Uint8Array(bytes)
  let binary = ''
  const chunk = 0x8000
  for (let i = 0; i < view.length; i += chunk) {
    binary += String.fromCharCode.apply(null, view.subarray(i, i + chunk))
  }
  return btoa(binary)
}

function fromBase64(text) {
  const binary = atob(text)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i)
  return bytes
}

/** DER 转 PEM，每行 64 字符 */
export function derToPem(der, label) {
  const base64 = toBase64(der)
  const lines = base64.match(/.{1,64}/g) || []
  return `-----BEGIN ${label}-----\n${lines.join('\n')}\n-----END ${label}-----\n`
}

async function deriveAesKey(passphrase, salt, iterations) {
  const subtle = requireWebCrypto()
  const encoder = new TextEncoder()
  const baseKey = await subtle.importKey(
    'raw',
    encoder.encode(passphrase),
    'PBKDF2',
    false,
    ['deriveKey'],
  )
  return subtle.deriveKey(
    { name: 'PBKDF2', salt, iterations, hash: 'SHA-256' },
    baseKey,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt'],
  )
}

/**
 * 生成 RSA-2048 密钥对。
 * @returns {Promise<{publicKeyPem: string, keyPair: CryptoKeyPair}>}
 */
export async function generateKeyPair() {
  const subtle = requireWebCrypto()
  const keyPair = await subtle.generateKey(KEY_ALGORITHM, true, ['sign', 'verify'])
  const spki = await subtle.exportKey('spki', keyPair.publicKey)
  return { publicKeyPem: derToPem(spki, 'PUBLIC KEY'), keyPair }
}

/**
 * 用口令加密私钥，生成可下载的备份对象。
 * @param {CryptoKeyPair} keyPair generateKeyPair 的返回值
 * @param {string} passphrase 保护私钥的口令，至少 8 个字符
 * @param {object} meta 附加信息，例如 { applicantName, email }
 */
export async function encryptPrivateKey(keyPair, passphrase, meta = {}) {
  if (!passphrase || passphrase.length < 8) {
    throw new Error('保护私钥的口令至少需要 8 个字符')
  }
  const subtle = requireWebCrypto()
  const salt = window.crypto.getRandomValues(new Uint8Array(SALT_BYTES))
  const iv = window.crypto.getRandomValues(new Uint8Array(IV_BYTES))
  const aesKey = await deriveAesKey(passphrase, salt, KDF_ITERATIONS)

  const pkcs8 = await subtle.exportKey('pkcs8', keyPair.privateKey)
  const ciphertext = await subtle.encrypt({ name: 'AES-GCM', iv }, aesKey, pkcs8)
  const publicKeyPem = derToPem(
    await subtle.exportKey('spki', keyPair.publicKey),
    'PUBLIC KEY',
  )

  return {
    version: PRIVATE_KEY_FORMAT_VERSION,
    type: 'encrypted-rsa-private-key',
    createdAt: new Date().toISOString(),
    subject: {
      applicantName: meta.applicantName || '',
      email: meta.email || '',
    },
    publicKeyPem,
    key: {
      algorithm: 'RSA',
      modulusLength: KEY_ALGORITHM.modulusLength,
      publicExponent: '65537',
      hash: 'SHA-256',
      format: 'pkcs8',
    },
    kdf: {
      name: 'PBKDF2',
      hash: 'SHA-256',
      iterations: KDF_ITERATIONS,
      salt: toBase64(salt),
    },
    cipher: {
      name: 'AES-GCM',
      keyLength: 256,
      iv: toBase64(iv),
      ciphertext: toBase64(ciphertext),
    },
  }
}

/**
 * 解密备份对象，取回私钥。用于演示「私钥可恢复」以及后续签名功能。
 */
export async function decryptPrivateKey(backup, passphrase) {
  const subtle = requireWebCrypto()
  if (!backup?.cipher?.ciphertext || !backup?.kdf?.salt || !backup?.cipher?.iv) {
    throw new Error('私钥文件格式不正确')
  }
  const salt = fromBase64(backup.kdf.salt)
  const iv = fromBase64(backup.cipher.iv)
  const iterations = backup.kdf.iterations || KDF_ITERATIONS
  const aesKey = await deriveAesKey(passphrase, salt, iterations)
  let pkcs8
  try {
    pkcs8 = await subtle.decrypt({ name: 'AES-GCM', iv }, aesKey, fromBase64(backup.cipher.ciphertext))
  } catch {
    throw new Error('口令不正确，无法解密私钥')
  }
  return subtle.importKey('pkcs8', pkcs8, KEY_ALGORITHM, true, ['sign'])
}

/** 校验用户粘贴的公钥是否为 PEM 格式的 SPKI 公钥 */
export function looksLikeSpkiPem(text) {
  if (typeof text !== 'string') return false
  return /-----BEGIN PUBLIC KEY-----[\s\S]+-----END PUBLIC KEY-----/.test(text.trim())
}

/** 用私钥对数据签名，返回 Base64 签名（SHA256withRSA） */
export async function signData(privateKey, text) {
  const subtle = requireWebCrypto()
  const signature = await subtle.sign(
    { name: 'RSASSA-PKCS1-v1_5' },
    privateKey,
    new TextEncoder().encode(text),
  )
  return toBase64(signature)
}
