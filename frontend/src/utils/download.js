/** 从 Content-Disposition 响应头解析文件名，兼容 RFC 5987 的 filename* 形式 */
export function filenameFromDisposition(header, fallback = 'download') {
  if (!header) return fallback
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(header)
  if (utf8) {
    try {
      return decodeURIComponent(utf8[1].trim())
    } catch {
      return utf8[1].trim()
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(header)
  if (plain) return plain[1].trim()
  return fallback
}

/** 触发浏览器保存 Blob */
export function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  link.remove()
  // 立即回收会让部分浏览器取消下载，稍作延迟
  setTimeout(() => URL.revokeObjectURL(url), 2000)
}

/** 把 Blob 转成可预览的 object URL，调用方负责在关闭时 revoke */
export function previewUrl(blob) {
  return URL.createObjectURL(blob)
}

export function revokePreviewUrl(url) {
  if (url) URL.revokeObjectURL(url)
}

export function formatBytes(bytes) {
  if (!Number.isFinite(bytes) || bytes < 0) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}
