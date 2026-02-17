const API_BASE = 'http://localhost:8080/api/v1'

export const api = {
  async get(url) {
    const res = await fetch(API_BASE + url)
    return res.json()
  },
  async post(url, data) {
    const res = await fetch(API_BASE + url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    })
    return res.json()
  },
  async put(url, data) {
    const res = await fetch(API_BASE + url, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    })
    return res.json()
  },
  async del(url) {
    const res = await fetch(API_BASE + url, { method: 'DELETE' })
    return res.json()
  },
  async upload(file) {
    const formData = new FormData()
    formData.append('file', file)
    const res = await fetch(API_BASE + '/upload/image', {
      method: 'POST',
      body: formData
    })
    return res.json()
  }
}

export { API_BASE }
