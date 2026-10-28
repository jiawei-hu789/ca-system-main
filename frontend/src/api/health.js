import http from './http'

export async function getHealth() {
  const response = await http.get('/health')
  const payload = response.data
  if (payload?.code !== 'SUCCESS' || payload?.data?.status !== 'UP') {
    throw new Error('后端未返回预期的健康状态')
  }
  return payload.data
}
