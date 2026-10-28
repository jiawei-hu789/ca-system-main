import http, { clearCsrfToken } from './http'

export async function fetchCsrf() {
  return http.get('/auth/csrf', { skipCsrf: true, skipAuthHandler: true, silent: true })
}

export async function register(username, password) {
  const data = await http.post('/auth/register', { username, password })
  return data
}

export async function login(username, password) {
  const data = await http.post('/auth/login', { username, password })
  // 登录成功后服务器清除了 CSRF Cookie，必须重新获取
  clearCsrfToken()
  return data
}

export async function logout() {
  try {
    await http.post('/auth/logout', null, { silent: true })
  } finally {
    clearCsrfToken()
  }
}

export async function fetchMe() {
  return http.get('/auth/me', { skipAuthHandler: true, silent: true })
}

export async function fetchHealth() {
  return http.get('/health', { skipAuthHandler: true, silent: true })
}
