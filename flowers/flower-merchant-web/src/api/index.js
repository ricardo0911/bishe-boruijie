const API_BASE = (import.meta.env.VITE_API_BASE || 'http://127.0.0.1:18080/api/v1').replace(/\/+$/, '')
const TOKEN_KEY = 'flower_merchant_token'
const ROLE_KEY = 'flower_merchant_role'
const NAME_KEY = 'flower_merchant_name'
const ACCOUNT_KEY = 'flower_merchant_account'
const LOGIN_TYPE_KEY = 'flower_merchant_login_type'
const MERCHANT_OPERATOR_NAME = 'Merchant Operator'

const STORAGE_KEYS = [TOKEN_KEY, ROLE_KEY, NAME_KEY, ACCOUNT_KEY, LOGIN_TYPE_KEY]

function normalizeMerchantAuthPayload(payload = {}) {
  const role = payload.role || payload.loginType
  if (role !== 'MERCHANT') {
    return payload
  }
  return {
    ...payload,
    displayName: payload.displayName || MERCHANT_OPERATOR_NAME
  }
}

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
  return normalizeMerchantAuthPayload({
    token,
    role,
    displayName: readStorage(NAME_KEY),
    account: readStorage(ACCOUNT_KEY),
    loginType: readStorage(LOGIN_TYPE_KEY) || 'MERCHANT'
  })
}

function saveStoredAuth(payload = {}, rememberMe = false) {
  const normalized = normalizeMerchantAuthPayload(payload)
  clearStoredAuth()
  const storage = getStorage(rememberMe ? 'local' : 'session')
  writeStorage(storage, TOKEN_KEY, normalized.token || '')
  writeStorage(storage, ROLE_KEY, normalized.role || '')
  writeStorage(storage, NAME_KEY, normalized.displayName || '')
  writeStorage(storage, ACCOUNT_KEY, normalized.account || '')
  writeStorage(storage, LOGIN_TYPE_KEY, normalized.loginType || 'MERCHANT')
}

function hasActiveSession() {
  return !!getStoredAuth()
}

const PUBLIC_PATHS = ['/merchant/login', '/merchant/register', '/merchant/change-password']

function authHeaders(extra = {}) {
  const auth = getStoredAuth()
  const headers = { ...extra }
  if (auth?.token) headers['X-Admin-Token'] = auth.token
  return headers
}

function isUnauthorizedResponse(body, status) {
  const message = String(body?.message || '')
  return status === 401 || status === 403 || body?.code === 'UNAUTHORIZED' || message.includes('无效的Token') || message.includes('未授权访问')
}

function redirectToLogin() {
  if (typeof window === 'undefined') return
  const currentPath = window.location.pathname || ''
  if (PUBLIC_PATHS.includes(currentPath)) return
  const redirect = encodeURIComponent((window.location.pathname || '/merchant') + (window.location.search || '') + (window.location.hash || ''))
  window.location.replace('/merchant/login?redirect=' + redirect)
}

async function parseJsonResponse(res) {
  let body
  try {
    body = await res.json()
  } catch {
    body = { success: false, code: 'BAD_RESPONSE', message: '服务返回了无效响应', data: null }
  }

  if (isUnauthorizedResponse(body, res?.status)) {
    clearStoredAuth()
    redirectToLogin()
  }

  return body
}

async function request(method, url, data) {
  const targetUrl = API_BASE + url
  if (method === 'GET') {
    const res = await fetch(targetUrl, { headers: authHeaders() })
    return parseJsonResponse(res)
  }
  if (method === 'DELETE') {
    const res = await fetch(targetUrl, { method: 'DELETE', headers: authHeaders() })
    return parseJsonResponse(res)
  }

  const res = await fetch(targetUrl, {
    method,
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(data)
  })
  return parseJsonResponse(res)
}

export const api = {
  async get(url) {
    return request('GET', url)
  },
  async post(url, data) {
    return request('POST', url, data)
  },
  async put(url, data) {
    return request('PUT', url, data)
  },
  async del(url) {
    return request('DELETE', url)
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
  async login(account, password) {
    const res = await fetch(API_BASE + '/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ loginType: 'MERCHANT', account, password })
    })
    const body = await parseJsonResponse(res)
    if (body?.success && body.data) {
      body.data = normalizeMerchantAuthPayload(body.data)
    }
    return body
  },
  async register(account, password, displayName) {
    const res = await fetch(API_BASE + '/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ loginType: 'MERCHANT', account, password, displayName })
    })
    const body = await parseJsonResponse(res)
    if (body?.success && body.data) {
      body.data = normalizeMerchantAuthPayload(body.data)
    }
    return body
  },
  async changePassword(account, oldPassword, newPassword) {
    const res = await fetch(API_BASE + '/auth/change-password', {
      method: 'POST',
      headers: authHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ loginType: 'MERCHANT', account, oldPassword, newPassword })
    })
    return parseJsonResponse(res)
  },
  async listReviews() {
    return this.get('/reviews')
  },
  async replyReview(id, reply) {
    return this.post(`/reviews/${id}/reply`, { reply })
  },
  async deleteReview(id) {
    return this.del(`/reviews/${id}`)
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
