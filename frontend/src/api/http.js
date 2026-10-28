import axios from 'axios'

/**
 * 后端所有写操作（POST/PUT/PATCH/DELETE）都需要 CSRF 令牌。
 * 令牌来自 GET /api/auth/csrf，并由 CookieCsrfTokenRepository 下发 XSRF-TOKEN Cookie。
 * 登录、退出成功后服务器会清除该 Cookie，因此必须重新获取。
 */
const WRITE_METHODS = new Set(['post', 'put', 'patch', 'delete'])

let csrfToken = null
let csrfInFlight = null
let unauthorizedHandler = null
let errorNotifier = null

/** 由 main.js 注册：会话失效时清理本地状态并跳转登录页 */
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

/** 由 main.js 注册：统一弹出错误提示 */
export function setErrorNotifier(notifier) {
  errorNotifier = notifier
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  // 证明材料单份可达 5MB、最多 3 份，留出足够上传时间
  timeout: 60000,
  withCredentials: true,
})

/** 登录、退出后调用，强制下次写操作重新取令牌 */
export function clearCsrfToken() {
  csrfToken = null
  csrfInFlight = null
}

/** 取 CSRF 令牌；并发调用共享同一个请求 */
export function ensureCsrfToken(force = false) {
  if (!force && csrfToken) return Promise.resolve(csrfToken)
  if (!csrfInFlight) {
    csrfInFlight = http
      .get('/auth/csrf', { skipCsrf: true, skipAuthHandler: true, silent: true })
      .then((data) => {
        csrfToken = data?.token || null
        return csrfToken
      })
      .finally(() => {
        csrfInFlight = null
      })
  }
  return csrfInFlight
}

function setHeader(config, name, value) {
  if (typeof config.headers?.set === 'function') config.headers.set(name, value)
  else config.headers = { ...(config.headers || {}), [name]: value }
}

http.interceptors.request.use(async (config) => {
  const method = String(config.method || 'get').toLowerCase()
  if (!config.skipCsrf && WRITE_METHODS.has(method)) {
    const token = await ensureCsrfToken()
    if (token) setHeader(config, 'X-XSRF-TOKEN', token)
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    // 文件下载保留完整响应，以便读取 Content-Disposition
    if (response.config.responseType === 'blob') return response
    const payload = response.data
    if (payload && typeof payload === 'object' && 'code' in payload) return payload.data
    return payload
  },
  async (error) => {
    const normalized = await normalizeError(error)
    const config = error?.config || {}
    if (!config.skipAuthHandler && normalized.status === 401) {
      // 会话已失效，旧的 CSRF 令牌随之作废，下次写操作必须重新获取
      clearCsrfToken()
      unauthorizedHandler?.(normalized)
      return Promise.reject(normalized)
    }
    if (!config.silent) errorNotifier?.(normalized)
    return Promise.reject(normalized)
  },
)

async function normalizeError(error) {
  if (error?.isNormalized) return error
  const response = error?.response
  let payload = response?.data
  // 下载接口出错时，错误体同样是 Blob，需要先读成文本
  if (payload instanceof Blob) {
    try {
      payload = JSON.parse(await payload.text())
    } catch {
      payload = null
    }
  }
  const status = response?.status ?? 0
  const code = payload?.code || (status === 0 ? 'NETWORK_ERROR' : 'REQUEST_FAILED')
  let message = payload?.message
  if (!message) {
    if (error?.code === 'ECONNABORTED') message = '请求超时，请稍后重试'
    else if (status === 0) message = '无法连接后端服务，请确认后端已在 8080 端口启动'
    else if (status === 413) message = '上传内容超过大小限制'
    else if (status === 415) message = '不支持的内容类型'
    else message = `请求失败（HTTP ${status}）`
  }
  const normalized = new Error(message)
  normalized.isNormalized = true
  normalized.status = status
  normalized.code = code
  normalized.config = error?.config
  normalized.response = response
  return normalized
}

export default http
