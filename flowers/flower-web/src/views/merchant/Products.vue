<template>
  <div>
    <div class="top-bar">
      <h1 class="page-title">商品管理</h1>
      <button class="btn btn-primary" @click="showAddForm">+ 新增商品</button>
    </div>
    <div class="card">
      <div class="card-header"><span class="card-title">商品列表</span></div>
      <div class="card-body">
        <div class="table-wrapper">
          <table>
            <thead><tr><th>ID</th><th>封面</th><th>商品名称</th><th>类型</th><th>分类</th><th>自动定价</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-if="loading"><td colspan="8" class="loading">加载中...</td></tr>
              <tr v-else-if="allProducts.length === 0"><td colspan="8" class="empty">暂无商品</td></tr>
              <tr v-for="p in allProducts" :key="p.id" v-else>
                <td>{{ p.id }}</td>
                <td>
                  <img v-if="p.coverImage" :src="resolveImageUrl(p.coverImage)" style="width:48px;height:48px;object-fit:cover;border-radius:6px">
                  <span v-else style="display:inline-block;width:48px;height:48px;background:#f5eef2;border-radius:6px"></span>
                </td>
                <td>{{ p.title }}</td>
                <td><span class="badge badge-info">{{ formatProductType(p.type) }}</span></td>
                <td>{{ formatProductCategory(p.category) }}</td>
                <td style="font-weight:600;color:var(--primary)">&yen;{{ formatPrice(p.unitPrice) }}</td>
                <td><span class="badge" :class="p.status === 'ON_SALE' ? 'badge-success' : 'badge-default'">{{ p.status === 'ON_SALE' ? '在售' : '下架' }}</span></td>
                <td><button class="btn btn-sm btn-outline" @click="editProduct(p.id)">编辑</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 商品表单弹窗 -->
    <div v-if="modalVisible" class="modal">
      <div class="modal-overlay" @click="closeModal"></div>
      <div class="modal-content">
        <h2>{{ editId ? '编辑商品' : '新增商品' }}</h2>
        <form @submit.prevent="saveProduct">
          <div class="form-group"><label>商品名称</label><input class="form-control" v-model="form.title" required></div>
          <div class="form-row">
            <div class="form-group"><label>类型</label>
              <select class="form-control" v-model="form.type"><option value="BOUQUET">花束</option><option value="PLANT">盆栽</option><option value="ACCESSORY">配件</option></select>
            </div>
            <div class="form-group"><label>分类</label><input class="form-control" v-model="form.category" placeholder="如：生日、婚礼"></div>
          </div>
          <div class="form-row">
            <div class="form-group"><label>基础价格</label><input type="number" step="0.01" class="form-control" v-model="form.basePrice"></div>
            <div class="form-group"><label>包装费</label><input type="number" step="0.01" class="form-control" v-model="form.packagingFee"></div>
            <div class="form-group"><label>配送费</label><input type="number" step="0.01" class="form-control" v-model="form.deliveryFee"></div>
          </div>
          <div class="form-group"><label>封面图片</label>
            <input type="file" class="form-control" accept="image/*" @change="previewImage">
            <div v-if="imagePreview" style="margin-top:8px"><img :src="imagePreview" style="max-width:200px;max-height:150px;border-radius:8px"></div>
          </div>
          <div class="form-group"><label>描述</label><textarea class="form-control" v-model="form.description" rows="3"></textarea></div>
          <div class="form-group" v-if="editId"><label>状态</label>
            <select class="form-control" v-model="form.status"><option value="ON_SALE">在售</option><option value="OFF_SHELF">下架</option></select>
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

const allProducts = ref([])
const loading = ref(true)
const modalVisible = ref(false)
const editId = ref(null)
const pendingImageFile = ref(null)
const imagePreview = ref('')
const form = ref({ title: '', type: 'BOUQUET', category: '', basePrice: 0, packagingFee: 0, deliveryFee: 0, coverImage: '', description: '', status: 'ON_SALE' })

const PRODUCT_TYPE_LABEL_MAP = {
  BOUQUET: '花束',
  PLANT: '盆栽',
  ACCESSORY: '配件'
}

const PRODUCT_CATEGORY_LABEL_MAP = {
  VALENTINE: '情人节',
  DAILY: '日常',
  BIRTHDAY: '生日',
  MOTHER_DAY: '母亲节',
  BUSINESS: '商务'
}

function formatProductType(type) {
  return PRODUCT_TYPE_LABEL_MAP[type] || type || '-'
}

function formatProductCategory(category) {
  return PRODUCT_CATEGORY_LABEL_MAP[category] || category || '-'
}

async function load() {
  loading.value = true
  try {
    const res = await api.get('/products')
    if (res.success && res.data) allProducts.value = res.data
  } catch (e) { console.error(e) }
  loading.value = false
}

function previewImage(e) {
  const file = e.target.files[0]
  pendingImageFile.value = file || null
  if (!file) { imagePreview.value = ''; return }
  const reader = new FileReader()
  reader.onload = ev => { imagePreview.value = ev.target.result }
  reader.readAsDataURL(file)
}

function showAddForm() {
  editId.value = null
  form.value = { title: '', type: 'BOUQUET', category: '', basePrice: 0, packagingFee: 0, deliveryFee: 0, coverImage: '', description: '', status: 'ON_SALE' }
  pendingImageFile.value = null
  imagePreview.value = ''
  modalVisible.value = true
}

function editProduct(id) {
  const p = allProducts.value.find(x => x.id === id)
  if (!p) return
  editId.value = id
  form.value = { title: p.title || '', type: p.type || 'BOUQUET', category: p.category || '', basePrice: 0, packagingFee: 0, deliveryFee: 0, coverImage: p.coverImage || '', description: '', status: p.status || 'ON_SALE' }
  pendingImageFile.value = null
  imagePreview.value = p.coverImage ? resolveImageUrl(p.coverImage) : ''
  modalVisible.value = true
}

async function saveProduct() {
  let coverImage = form.value.coverImage
  if (pendingImageFile.value) {
    try {
      const uploadRes = await api.upload(pendingImageFile.value)
      if (uploadRes.success && uploadRes.data) { coverImage = uploadRes.data }
      else { showToast('图片上传失败: ' + (uploadRes.message || '未知错误')); return }
    } catch (err) { showToast('图片上传失败: ' + err.message); return }
  }
  const body = {
    title: form.value.title,
    type: form.value.type,
    category: form.value.category,
    basePrice: form.value.basePrice,
    packagingFee: form.value.packagingFee,
    deliveryFee: form.value.deliveryFee,
    coverImage,
    description: form.value.description
  }
  try {
    if (editId.value) {
      body.status = form.value.status
      await api.put('/products/' + editId.value, body)
    } else {
      await api.post('/products', body)
    }
    showToast('保存成功')
    closeModal()
    load()
  } catch (err) { showToast('保存失败: ' + err.message) }
}

function closeModal() { modalVisible.value = false }

onMounted(load)
</script>
