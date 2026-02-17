<template>
  <div>
    <div class="top-bar">
      <h1 class="page-title">花材管理</h1>
      <button class="btn btn-primary" @click="showAddForm">+ 新增花材</button>
    </div>
    <div class="card">
      <div class="card-header"><span class="card-title">花材列表</span></div>
      <div class="card-body">
        <div class="table-wrapper">
          <table>
            <thead>
              <tr><th>ID</th><th>图片</th><th>名称</th><th>分类</th><th>单位</th><th>售价</th><th>成本</th><th>花期(天)</th><th>预警阈值</th><th>状态</th><th>操作</th></tr>
            </thead>
            <tbody>
              <tr v-if="loading"><td colspan="11" class="loading">加载中...</td></tr>
              <tr v-else-if="allFlowers.length === 0"><td colspan="11" class="empty">暂无花材</td></tr>
              <tr v-for="f in allFlowers" :key="f.id" v-else>
                <td>{{ f.id }}</td>
                <td>
                  <img v-if="f.image_url" :src="resolveImageUrl(f.image_url)" style="width:40px;height:40px;object-fit:cover;border-radius:6px">
                  <span v-else style="display:inline-block;width:40px;height:40px;background:#f5eef2;border-radius:6px"></span>
                </td>
                <td>{{ f.name }}</td>
                <td><span class="badge badge-info">{{ f.category }}</span></td>
                <td>{{ f.unit }}</td>
                <td style="color:var(--primary)">&yen;{{ formatPrice(f.sale_price) }}</td>
                <td>&yen;{{ formatPrice(f.cost_price) }}</td>
                <td>{{ f.shelf_life_days }}</td>
                <td>{{ f.warn_threshold }}</td>
                <td><span class="badge" :class="f.enabled ? 'badge-success' : 'badge-default'">{{ f.enabled ? '启用' : '禁用' }}</span></td>
                <td style="white-space:nowrap">
                  <button class="btn btn-sm btn-outline" @click="editFlower(f.id)">编辑</button>
                  <button v-if="f.enabled" class="btn btn-sm btn-outline" style="color:var(--warning);border-color:var(--warning)" @click="toggleFlower(f.id, false)">禁用</button>
                  <button v-else class="btn btn-sm btn-outline" style="color:var(--success);border-color:var(--success)" @click="toggleFlower(f.id, true)">启用</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 花材表单弹窗 -->
    <div v-if="modalVisible" class="modal">
      <div class="modal-overlay" @click="closeModal"></div>
      <div class="modal-content">
        <h2>{{ editId ? '编辑花材' : '新增花材' }}</h2>
        <form @submit.prevent="saveFlower">
          <div class="form-row">
            <div class="form-group"><label>名称</label><input class="form-control" v-model="form.name" required></div>
            <div class="form-group"><label>分类</label><input class="form-control" v-model="form.category" placeholder="玫瑰/百合/配材"></div>
          </div>
          <div class="form-row">
            <div class="form-group"><label>单位</label><input class="form-control" v-model="form.unit"></div>
            <div class="form-group"><label>售价</label><input type="number" step="0.01" class="form-control" v-model="form.salePrice" required></div>
            <div class="form-group"><label>成本价</label><input type="number" step="0.01" class="form-control" v-model="form.costPrice" required></div>
          </div>
          <div class="form-row">
            <div class="form-group"><label>花期(天)</label><input type="number" class="form-control" v-model="form.shelfLifeDays"></div>
            <div class="form-group"><label>预警阈值</label><input type="number" class="form-control" v-model="form.warnThreshold"></div>
          </div>
          <div class="form-group"><label>花材图片</label>
            <input type="file" class="form-control" accept="image/*" @change="previewFlowerImage">
            <div v-if="imagePreview" style="margin-top:8px"><img :src="imagePreview" style="max-width:200px;max-height:150px;border-radius:8px"></div>
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
import { formatPrice, resolveImageUrl, showToast } from '../../utils'

const allFlowers = ref([])
const loading = ref(true)
const modalVisible = ref(false)
const editId = ref(null)
const pendingFlowerImage = ref(null)
const imagePreview = ref('')
const form = ref({ name: '', category: '', unit: '枝', salePrice: 0, costPrice: 0, shelfLifeDays: 7, warnThreshold: 10, image: '' })

async function load() {
  loading.value = true
  try {
    const res = await api.get('/flowers')
    if (res.success && res.data) allFlowers.value = res.data
  } catch (e) { console.error(e) }
  loading.value = false
}

function previewFlowerImage(e) {
  const file = e.target.files[0]
  pendingFlowerImage.value = file || null
  if (!file) { imagePreview.value = ''; return }
  const reader = new FileReader()
  reader.onload = ev => { imagePreview.value = ev.target.result }
  reader.readAsDataURL(file)
}

async function toggleFlower(id, enabled) {
  const f = allFlowers.value.find(x => x.id === id)
  if (!f) return
  try {
    await api.put('/flowers/' + id, {
      name: f.name, category: f.category, unit: f.unit,
      salePrice: f.sale_price, costPrice: f.cost_price,
      shelfLifeDays: f.shelf_life_days, warnThreshold: f.warn_threshold,
      enabled, image: f.image_url || ''
    })
    showToast(enabled ? '已启用' : '已禁用')
    load()
  } catch (e) { showToast('操作失败') }
}

function showAddForm() {
  editId.value = null
  form.value = { name: '', category: '', unit: '枝', salePrice: 0, costPrice: 0, shelfLifeDays: 7, warnThreshold: 10, image: '' }
  pendingFlowerImage.value = null
  imagePreview.value = ''
  modalVisible.value = true
}

function editFlower(id) {
  const f = allFlowers.value.find(x => x.id === id)
  if (!f) return
  editId.value = id
  form.value = {
    name: f.name || '', category: f.category || '', unit: f.unit || '枝',
    salePrice: f.sale_price || 0, costPrice: f.cost_price || 0,
    shelfLifeDays: f.shelf_life_days || 7, warnThreshold: f.warn_threshold || 10,
    image: f.image_url || ''
  }
  pendingFlowerImage.value = null
  imagePreview.value = f.image_url ? resolveImageUrl(f.image_url) : ''
  modalVisible.value = true
}

async function saveFlower() {
  let image = form.value.image
  if (pendingFlowerImage.value) {
    try {
      const uploadRes = await api.upload(pendingFlowerImage.value)
      if (uploadRes.success && uploadRes.data) { image = uploadRes.data }
      else { showToast('图片上传失败: ' + (uploadRes.message || '未知错误')); return }
    } catch (err) { showToast('图片上传失败: ' + err.message); return }
  }
  const body = {
    name: form.value.name,
    category: form.value.category,
    unit: form.value.unit,
    salePrice: parseFloat(form.value.salePrice),
    costPrice: parseFloat(form.value.costPrice),
    shelfLifeDays: parseInt(form.value.shelfLifeDays),
    warnThreshold: parseInt(form.value.warnThreshold),
    image
  }
  try {
    if (editId.value) {
      await api.put('/flowers/' + editId.value, body)
    } else {
      await api.post('/flowers', body)
    }
    showToast('保存成功')
    closeModal()
    load()
  } catch (err) { showToast('保存失败: ' + err.message) }
}

function closeModal() { modalVisible.value = false }

onMounted(load)
</script>
