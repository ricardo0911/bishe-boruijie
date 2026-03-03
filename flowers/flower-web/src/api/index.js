const API_BASE = (import.meta.env.VITE_API_BASE || 'http://127.0.0.1:18080/api/v1').replace(/\/+$/, '')
const ADMIN_TOKEN =
  (typeof localStorage !== 'undefined' && localStorage.getItem('admin_token')) ||
  import.meta.env.VITE_ADMIN_TOKEN ||
  'please-change-admin-token'

function authHeaders(extra = {}) {
  return {
    ...extra,
    'X-Admin-Token': ADMIN_TOKEN,
    'X-User-Roles': 'MERCHANT'
  }
}

export const api = {
  async get(url) {
    const res = await fetch(API_BASE + url, {
      headers: authHeaders()
    })
    return res.json()
  },
  async post(url, data) {
    const res = await fetch(API_BASE + url, {
      method: 'POST',
      headers: authHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(data)
    })
    return res.json()
  },
  async put(url, data) {
    const res = await fetch(API_BASE + url, {
      method: 'PUT',
      headers: authHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(data)
    })
    return res.json()
  },
  async del(url) {
    const res = await fetch(API_BASE + url, {
      method: 'DELETE',
      headers: authHeaders()
    })
    return res.json()
  },
  async upload(file) {
    const formData = new FormData()
    formData.append('file', file)
    const res = await fetch(API_BASE + '/upload/image', {
      method: 'POST',
      headers: authHeaders(),
      body: formData
    })
    return res.json()
  }
}

export { API_BASE }
