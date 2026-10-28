import { reactive } from 'vue'
import { fetchMe } from '../api/auth'

/**
 * 全局登录态。role 取值为 USER 或 ADMIN。
 * 页面只读取 user / ready，写入一律走下面的函数。
 */
export const sessionState = reactive({
  user: null,
  ready: false,
  loading: null,
})

export function isLoggedIn() {
  return Boolean(sessionState.user)
}

export function isAdmin() {
  return sessionState.user?.role === 'ADMIN'
}

export function setUser(user) {
  sessionState.user = user || null
  sessionState.ready = true
  return sessionState.user
}

export function resetSession() {
  sessionState.user = null
  sessionState.ready = true
  sessionState.loading = null
}

/** 路由守卫调用：首次进入或强制刷新时向后端确认身份 */
export function ensureSession(force = false) {
  if (!force && sessionState.ready) return Promise.resolve(sessionState.user)
  if (!sessionState.loading) {
    sessionState.loading = fetchMe()
      .then((user) => setUser(user))
      .catch(() => resetSession())
      .finally(() => {
        sessionState.loading = null
      })
  }
  return sessionState.loading
}
