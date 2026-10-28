/** 后端返回带时区的 ISO 8601 字符串，统一按北京时间展示 */
const formatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

export function formatDateTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return formatter.format(date).replace(/\//g, '-')
}

export function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
    .format(date)
    .replace(/\//g, '-')
}

const APPLICATION_STATUS = {
  PENDING: { label: '待审核', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
}

const CERTIFICATE_STATUS = {
  VALID: { label: '有效', type: 'success' },
  REVOKED: { label: '已吊销', type: 'danger' },
  EXPIRED: { label: '已过期', type: 'info' },
  NOT_YET_VALID: { label: '尚未生效', type: 'warning' },
}

const REVOCATION_STATUS = {
  PENDING: { label: '待处理', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
}

const REVOCATION_CHECK = {
  GOOD: { label: '未吊销', type: 'success' },
  REVOKED: { label: '已吊销', type: 'danger' },
  UNKNOWN: { label: '未知证书', type: 'info' },
}

const ROLE = {
  USER: { label: '普通用户', type: 'info' },
  ADMIN: { label: '管理员', type: 'danger' },
}

export function applicationStatus(status) {
  return APPLICATION_STATUS[status] || { label: status || '未知', type: 'info' }
}

export function certificateStatus(status) {
  return CERTIFICATE_STATUS[status] || { label: status || '未知', type: 'info' }
}

export function revocationStatus(status) {
  return REVOCATION_STATUS[status] || { label: status || '未知', type: 'info' }
}

export function revocationCheck(status) {
  return REVOCATION_CHECK[status] || { label: status || '未知', type: 'info' }
}

export function roleLabel(role) {
  return ROLE[role] || { label: role || '未知', type: 'info' }
}

/** 序列号很长，列表里截断显示，详情页展示完整值 */
export function shortSerial(serial, head = 8, tail = 6) {
  if (!serial) return '—'
  const text = String(serial)
  if (text.length <= head + tail + 1) return text
  return `${text.slice(0, head)}…${text.slice(-tail)}`
}
