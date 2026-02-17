import { API_BASE } from '../api'

export function showToast(msg, duration = 2000) {
  let toast = document.getElementById('global-toast')
  if (!toast) {
    toast = document.createElement('div')
    toast.id = 'global-toast'
    toast.className = 'toast'
    document.body.appendChild(toast)
  }
  toast.textContent = msg
  toast.classList.add('show')
  setTimeout(() => toast.classList.remove('show'), duration)
}

export function toNumber(value, fallback = 0) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

export function formatPrice(price) {
  return toNumber(price, 0).toFixed(2)
}

export function resolvePrice(data) {
  if (!data) return 0
  return toNumber(data.unitPrice ?? data.autoPrice ?? data.unit_price ?? 0, 0)
}

export function pickText(data, ...keys) {
  for (const key of keys) {
    if (data && data[key] !== undefined && data[key] !== null) {
      return String(data[key])
    }
  }
  return ''
}

export function resolveImageUrl(url) {
  if (!url) return ''
  const text = String(url).trim()
  if (!text) return ''
  if (/^https?:\/\//i.test(text)) return text
  if (text.startsWith('//')) return `https:${text}`

  const base = API_BASE || 'http://localhost:8080/api/v1'
  const apiIndex = base.indexOf('/api/')
  const origin = apiIndex > -1 ? base.slice(0, apiIndex) : base.replace(/\/+$/, '')

  if (text.startsWith('/')) {
    return `${origin}${text}`
  }
  return `${origin}/${text}`
}
