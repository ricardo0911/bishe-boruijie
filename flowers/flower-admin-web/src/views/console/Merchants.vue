<template>
  <div>
    <div class="top-bar">
      <h1 class="page-title">商家管理</h1>
      <button class="btn btn-primary" @click="showAddForm">+ 新增商家</button>
    </div>
    <div class="card">
      <div class="card-header"><span class="card-title">商家列表</span></div>
      <div class="card-body">
        <div class="table-wrapper">
          <table>
            <thead>
              <tr><th>ID</th><th>商家名称</th><th>联系电话</th><th>邮箱</th><th>地址</th><th>状态</th><th>操作</th></tr>
            </thead>
            <tbody>
              <tr v-if="loading"><td colspan="7" class="loading">加载中...</td></tr>
              <tr v-else-if="allMerchants.length === 0"><td colspan="7" class="empty">暂无商家数据</td></tr>
              <tr v-for="m in allMerchants" :key="m.id" v-else>
                <td>{{ m.id }}</td>
                <td style="font-weight:500">{{ m.name }}</td>
                <td>{{ m.contactPhone || '-' }}</td>
                <td>{{ m.email || '-' }}</td>
                <td style="font-size:12px">{{ m.address || '-' }}</td>
                <td><span class="badge" :class="m.status === 'ACTIVE' ? 'badge-success' : 'badge-danger'">{{ m.status === 'ACTIVE' ? '正常' : '禁用' }}</span></td>
                <td>
                  <button class="btn btn-sm btn-outline" @click="editMerchant(m.id)">编辑</button>
                  <button class="btn btn-sm" :class="m.status === 'ACTIVE' ? 'btn-danger' : 'btn-success'" @click="toggleStatus(m.id)">{{ m.status === 'ACTIVE' ? '禁用' : '启用' }}</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 商家表单弹窗 -->
    <div v-if="modalVisible" class="modal">
      <div class="modal-overlay" @click="closeModal"></div>
      <div class="modal-content">
        <h2>{{ editId ? '编辑商家' : '新增商家' }}</h2>
        <form @submit.prevent="saveMerchant">
          <div class="form-group"><label>商家名称</label><input class="form-control" v-model="form.name" required></div>
          <div class="form-row">
            <div class="form-group"><label>联系电话</label><input class="form-control" v-model="form.contactPhone"></div>
            <div class="form-group"><label>邮箱</label><input type="email" class="form-control" v-model="form.email"></div>
          </div>
          <div class="form-group"><label>地址</label><input class="form-control" v-model="form.address"></div>
          <div class="form-group" v-if="editId"><label>状态</label>
            <select class="form-control" v-model="form.status">
              <option value="ACTIVE">正常</option>
              <option value="DISABLED">禁用</option>
            </select>
          </div>
          <div class="form-actions">
            <button type="button" class="btn btn-outline" @click="closeModal">取消</button>
            <button type="submit" class="btn btn-primary">保存</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../../api'
import { showToast } from '../../utils'

const allMerchants = ref([])
const loading = ref(true)
const modalVisible = ref(false)
const editId = ref(null)
const form = ref({ name: '', contactPhone: '', email: '', address: '', status: 'ACTIVE' })

async function load() {
  loading.value = true
  try {
    const res = await api.get('/merchants')
    if (res.success && Array.isArray(res.data)) allMerchants.value = res.data
    else allMerchants.value = []
  } catch (e) { allMerchants.value = [] }
  loading.value = false
}

function showAddForm() {
  editId.value = null
  form.value = { name: '', contactPhone: '', email: '', address: '', status: 'ACTIVE' }
  modalVisible.value = true
}

function editMerchant(id) {
  const m = allMerchants.value.find(x => x.id === id)
  if (!m) return
  editId.value = id
  form.value = { name: m.name || '', contactPhone: m.contactPhone || '', email: m.email || '', address: m.address || '', status: m.status || 'ACTIVE' }
  modalVisible.value = true
}

async function saveMerchant() {
  const body = { name: form.value.name, contactPhone: form.value.contactPhone, email: form.value.email, address: form.value.address }
  try {
    if (editId.value) {
      body.status = form.value.status
      await api.put('/merchants/' + editId.value, body)
    } else {
      await api.post('/merchants', body)
    }
    showToast('保存成功')
    closeModal()
    load()
  } catch (err) { showToast('保存失败: ' + err.message) }
}

async function toggleStatus(id) {
  const m = allMerchants.value.find(x => x.id === id)
  if (!m) return
  const newStatus = m.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  if (!confirm(`确认${newStatus === 'ACTIVE' ? '启用' : '禁用'}该商家？`)) return
  try {
    await api.put('/merchants/' + id, { name: m.name, contactPhone: m.contactPhone, email: m.email, address: m.address, status: newStatus })
    load()
  } catch (err) { showToast('操作失败: ' + err.message) }
}

function closeModal() { modalVisible.value = false }

onMounted(load)
</script>
