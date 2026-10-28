import http from './http'
import { filenameFromDisposition } from '../utils/download'

/**
 * 创建认证申请。字段名必须与后端 @RequestParam 一致：
 * applicantName / email / publicKeyPem / evidenceFiles
 */
export async function createApplication({ applicantName, email, publicKeyPem, evidenceFiles }) {
  const form = new FormData()
  form.append('applicantName', applicantName)
  form.append('email', email)
  form.append('publicKeyPem', publicKeyPem)
  for (const file of evidenceFiles) form.append('evidenceFiles', file)
  return http.post('/applications', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  })
}

export async function listMyApplications({ limit = 20, offset = 0 } = {}) {
  return http.get('/applications/mine', { params: { limit, offset } })
}

export async function getApplication(id) {
  return http.get(`/applications/${id}`)
}

export async function listAdminApplications({ status, limit = 20, offset = 0 } = {}) {
  const params = { limit, offset }
  if (status) params.status = status
  return http.get('/admin/applications', { params })
}

/** decision: APPROVE 或 REJECT；驳回时 comment 必填 */
export async function reviewApplication(id, { decision, comment }) {
  return http.post(`/admin/applications/${id}/review`, { decision, comment: comment || null })
}

/**
 * 下载证明材料。注意参数是「材料 ID」而不是「申请 ID」，
 * 材料 ID 来自 application.evidenceFiles[].id。
 */
export async function downloadEvidence(evidenceId, fallbackName = 'evidence') {
  const response = await http.get(`/evidence/${evidenceId}/download`, { responseType: 'blob' })
  return {
    blob: response.data,
    filename: filenameFromDisposition(
      response.headers?.['content-disposition'],
      fallbackName,
    ),
  }
}
