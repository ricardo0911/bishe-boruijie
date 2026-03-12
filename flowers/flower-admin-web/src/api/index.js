const API_BASE = (import.meta.env.VITE_API_BASE || 'http://127.0.0.1:18080/api/v1').replace(/\/+$/, '')
const TOKEN_KEY = 'flower_admin_token'
const ROLE_KEY = 'flower_admin_role'
const NAME_KEY = 'flower_admin_name'
const ACCOUNT_KEY = 'flower_admin_account'
const LOGIN_TYPE_KEY = 'flower_admin_login_type'

const STORAGE_KEYS = [TOKEN_KEY, ROLE_KEY, NAME_KEY, ACCOUNT_KEY, LOGIN_TYPE_KEY]

function getStorage(type) {
  if (typeof window === 'undefined') return null
  return type === 'local' ? window.localStorage : window.sessionStorage
}

function readStorage(key) {
  return getStorage('local')?.getItem(key) || getStorage('session')?.getItem(key) || ''
}

function writeStorage(storage, key, value) {
  if (!storage) return
  if (value) {
    storage.setItem(key, value)
    return
  }
  storage.removeItem(key)
}

function clearStoredAuth() {
  for (const key of STORAGE_KEYS) {
    getStorage('local')?.removeItem(key)
    getStorage('session')?.removeItem(key)
  }
}

function getStoredAuth() {
  const token = readStorage(TOKEN_KEY)
  const role = readStorage(ROLE_KEY)
  if (!token || !role) return null

  return {
    token,
    role,
    displayName: readStorage(NAME_KEY),
    account: readStorage(ACCOUNT_KEY),
    loginType: readStorage(LOGIN_TYPE_KEY) || 'ADMIN'
  }
}

function saveStoredAuth(payload = {}, rememberMe = false) {
  clearStoredAuth()
  const storage = getStorage(rememberMe ? 'local' : 'session')
  writeStorage(storage, TOKEN_KEY, payload.token || '')
  writeStorage(storage, ROLE_KEY, payload.role || '')
  writeStorage(storage, NAME_KEY, payload.displayName || '')
  writeStorage(storage, ACCOUNT_KEY, payload.account || '')
  writeStorage(storage, LOGIN_TYPE_KEY, payload.loginType || 'ADMIN')
}

function hasActiveSession() {
  return !!getStoredAuth()
}

function authHeaders(extra = {}) {
  const auth = getStoredAuth()
  const headers = { ...extra }
  if (auth?.token) headers['X-Admin-Token'] = auth.token
  return headers
}

function redirectToLoginOnAuthFailure() {
  if (typeof window === 'undefined') return
  const currentPath = window.location.pathname || ''
  if (currentPath.startsWith('/admin/login')) return

  const redirectPath = currentPath.startsWith('/admin')
    ? currentPath.slice('/admin'.length) || '/'
    : currentPath || '/'
  const target = new URL('/admin/login', window.location.origin)
  target.searchParams.set('notice', 'session-expired')
  target.searchParams.set('redirect', redirectPath + (window.location.search || ''))

  clearStoredAuth()
  window.alert('登录状态已失效，请重新登录')
  window.location.replace(target.toString())
}

async function parseJsonResponse(res) {
  let payload
  try {
    payload = await res.json()
  } catch {
    payload = { success: false, code: 'BAD_RESPONSE', message: '服务返回了无效响应', data: null }
  }

  const authFailed = !res.ok && (payload?.code === 'UNAUTHORIZED' || payload?.code === 'FORBIDDEN')
  if (authFailed) {
    redirectToLoginOnAuthFailure()
  }

  return payload
}

export const api = {
  async get(url) {
    const res = await fetch(API_BASE + url, {
      headers: authHeaders()
    })
    return parseJsonResponse(res)
  },
  async post(url, data) {
    const res = await fetch(API_BASE + url, {
      method: 'POST',
      headers: authHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(data)
    })
    return parseJsonResponse(res)
  },
  async put(url, data) {
    const res = await fetch(API_BASE + url, {
      method: 'PUT',
      headers: authHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(data)
    })
    return parseJsonResponse(res)
  },
  async del(url) {
    const res = await fetch(API_BASE + url, {
      method: 'DELETE',
      headers: authHeaders()
    })
    return parseJsonResponse(res)
  },
  async upload(file) {
    const formData = new FormData()
    formData.append('file', file)
    const res = await fetch(API_BASE + '/upload/image', {
      method: 'POST',
      headers: authHeaders(),
      body: formData
    })
    return parseJsonResponse(res)
  },
  async listReviews() {
    return this.get('/reviews')
  },
  async deleteReview(id) {
    return this.del(`/reviews/${id}`)
  },
  async replyReview(id, reply) {
    return this.post(`/reviews/${id}/reply`, { reply })
  },
  async listSupportTickets(params = {}) {
    const search = new URLSearchParams()
    search.set('limit', String(Number(params.limit || 100) || 100))
    if (params.status) search.set('status', params.status)
    if (params.keyword) search.set('keyword', params.keyword)
    return this.get(`/support-tickets?${search.toString()}`)
  },
  async getSupportTicket(id) {
    return this.get(`/support-tickets/${id}`)
  },
  async processSupportTicket(id, payload) {
    return this.post(`/support-tickets/${id}/process`, payload)
  },
  async login(account, password) {
    const res = await fetch(API_BASE + '/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ loginType: 'ADMIN', account, password })
    })
    return parseJsonResponse(res)
  },
  async register(account, password, displayName) {
    const res = await fetch(API_BASE + '/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ loginType: 'ADMIN', account, password, displayName })
    })
    return parseJsonResponse(res)
  },
  async changePassword(account, oldPassword, newPassword) {
    const res = await fetch(API_BASE + '/auth/change-password', {
      method: 'POST',
      headers: authHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ loginType: 'ADMIN', account, oldPassword, newPassword })
    })
    return parseJsonResponse(res)
  },
  async logout() {
    const res = await fetch(API_BASE + '/auth/logout', {
      method: 'POST',
      headers: authHeaders()
    })
    return parseJsonResponse(res)
  }
}

export { API_BASE, clearStoredAuth, getStoredAuth, hasActiveSession, saveStoredAuth }
