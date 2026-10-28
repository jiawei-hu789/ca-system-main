import http from './http'
import { filenameFromDisposition } from '../utils/download'

export async function listMyCertificates({ limit = 20, offset = 0 } = {}) {
  return http.get('/certificates/mine', { params: { limit, offset } })
}

export async function listAdminCertificates({ limit = 20, offset = 0 } = {}) {
  return http.get('/admin/certificates', { params: { limit, offset } })
}

export async function getCertificate(serial) {
  return http.get(`/certificates/${encodeURIComponent(serial)}`)
}

export async function downloadCertificate(serial) {
  const response = await http.get(`/certificates/${encodeURIComponent(serial)}/download`, {
    responseType: 'blob',
  })
  return {
    blob: response.data,
    filename: filenameFromDisposition(
      response.headers?.['content-disposition'],
      `certificate-${String(serial).toLowerCase()}.pem`,
    ),
  }
}

export async function downloadRootCertificate() {
  const response = await http.get('/ca/certificate', { responseType: 'blob' })
  return {
    blob: response.data,
    filename: filenameFromDisposition(response.headers?.['content-disposition'], 'ca-root.pem'),
  }
}

/** 用户提交吊销申请，reason 1-500 字符 */
export async function requestRevocation(serial, reason) {
  return http.post(`/certificates/${encodeURIComponent(serial)}/revocation-requests`, { reason })
}

/** 管理员执行吊销，reason 1-500 字符 */
export async function revokeCertificate(serial, reason) {
  return http.post(`/admin/certificates/${encodeURIComponent(serial)}/revoke`, { reason })
}

/** 上传单张 PEM 或 DER 证书进行验证，字段名必须是 certificate */
export async function verifyCertificate(file) {
  const form = new FormData()
  form.append('certificate', file)
  return http.post('/certificates/verify', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
