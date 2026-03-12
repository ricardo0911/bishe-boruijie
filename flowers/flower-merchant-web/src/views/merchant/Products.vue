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
            <thead>
              <tr>
                <th>ID</th>
                <th>封面</th>
                <th>商品名称</th>
                <th>类型</th>
                <th>分类</th>
                <th>自动定价</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading"><td colspan="8" class="loading">加载中...</td></tr>
              <tr v-else-if="allProducts.length === 0"><td colspan="8" class="empty">暂无商品</td></tr>
              <tr v-for="product in allProducts" :key="product.id" v-else>
                <td>{{ product.id }}</td>
                <td>
                  <img
                    v-if="product.coverImage"
                    :src="resolveImageUrl(product.coverImage)"
                    style="width:48px;height:48px;object-fit:cover;border-radius:6px"
                  >
                  <span v-else style="display:inline-block;width:48px;height:48px;background:#f5eef2;border-radius:6px"></span>
                </td>
                <td>
                  <div style="font-weight:600;color:var(--text-primary)">{{ product.title }}</div>
                  <div style="margin-top:6px;font-size:12px;line-height:1.5;color:var(--text-secondary)">
                    组成：{{ formatBomItemsSummary(product.bomItems, product.compositionSummary) }}
                  </div>
                </td>
                <td><span class="badge badge-info">{{ formatProductType(product.type) }}</span></td>
                <td>{{ formatProductCategory(product.category) }}</td>
                <td style="font-weight:600;color:var(--primary)">&yen;{{ formatPrice(product.unitPrice) }}</td>
                <td>
                  <span class="badge" :class="product.status === 'ON_SALE' ? 'badge-success' : 'badge-default'">
                    {{ product.status === 'ON_SALE' ? '在售' : '下架' }}
                  </span>
                </td>
                <td>
                  <div style="display:flex;gap:8px;flex-wrap:wrap">
                    <button class="btn btn-sm btn-outline" @click="editProduct(product.id)">编辑</button>
                    <button
                      class="btn btn-sm btn-danger"
                      :disabled="deletingId === product.id"
                      @click="deleteProduct(product.id)"
                    >{{ deletingId === product.id ? '删除中...' : '删除' }}</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div v-if="modalVisible" class="modal">
      <div class="modal-overlay" @click="closeModal"></div>
      <div class="modal-content">
        <h2>{{ editId ? '编辑商品' : '新增商品' }}</h2>
        <form @submit.prevent="saveProduct">
          <div class="form-group">
            <label>商品名称</label>
            <input class="form-control" v-model="form.title" required>
          </div>

          <div class="form-row">
            <div class="form-group">
              <label>类型</label>
              <select class="form-control" v-model="form.type">
                <option value="BOUQUET">花束</option>
                <option value="PLANT">盆栽</option>
                <option value="ACCESSORY">配件</option>
              </select>
            </div>
            <div class="form-group">
              <label>分类</label>
              <input class="form-control" v-model="form.category" placeholder="如：生日、婚礼">
            </div>
          </div>

          <div class="form-row">
            <div class="form-group"><label>基础价格</label><input type="number" step="0.01" class="form-control" v-model="form.basePrice"></div>
            <div class="form-group"><label>包装费</label><input type="number" step="0.01" class="form-control" v-model="form.packagingFee"></div>
            <div class="form-group"><label>配送费</label><input type="number" step="0.01" class="form-control" v-model="form.deliveryFee"></div>
          </div>

          <div class="form-group">
            <label>封面图片</label>
            <input ref="fileInputRef" type="file" accept="image/*" style="display:none" @change="previewImage">
            <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
              <button type="button" class="btn btn-outline" @click="triggerImagePicker">
                {{ pendingImageFile ? '重新选择图片' : (imagePreview ? '更换图片' : '上传图片') }}
              </button>
              <span style="font-size:13px;color:#7a6b75">
                {{ pendingImageFile ? pendingImageFile.name : (imagePreview ? '当前已存在封面，不更换则继续使用原图' : '未选择文件') }}
              </span>
            </div>
            <div v-if="imagePreview" style="margin-top:8px">
              <img :src="imagePreview" style="max-width:200px;max-height:150px;border-radius:8px">
            </div>
          </div>

          <div class="form-group">
            <div style="display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:12px">
              <label style="margin:0">商品组成</label>
              <button type="button" class="btn btn-sm btn-outline" @click="addBomItem">+ 添加花材</button>
            </div>

            <div v-if="form.bomItems.length === 0" style="padding:12px 14px;border-radius:10px;background:#fff7fa;color:#8d7480">
              还没有配置花材。未配置 BOM 的商品无法正常扣减库存。
            </div>

            <div
              v-for="(item, index) in form.bomItems"
              :key="`bom-${index}`"
              style="display:grid;grid-template-columns:1.6fr 1fr auto;gap:12px;align-items:center;margin-bottom:10px"
            >
              <select class="form-control" v-model="item.flowerId">
                <option value="">请选择花材</option>
                <option v-for="flower in flowerOptions" :key="flower.id" :value="String(flower.id)">
                  {{ flower.name }}
                </option>
              </select>
              <input type="number" min="0.01" step="0.01" class="form-control" v-model="item.dosage" placeholder="用量">
              <button type="button" class="btn btn-sm btn-danger" @click="removeBomItem(index)">删除</button>
            </div>

            <div style="padding:12px 14px;border-radius:10px;background:#fff7fa;color:#6f5a66;line-height:1.6">
              当前组成：{{ formatBomItemsSummary(form.bomItems) }}
            </div>
          </div>

          <div class="form-group">
            <label>描述</label>
            <textarea class="form-control" v-model="form.description" rows="3"></textarea>
          </div>

          <div class="form-group" v-if="editId">
            <label>状态</label>
            <select class="form-control" v-model="form.status">
              <option value="ON_SALE">在售</option>
              <option value="OFF_SHELF">下架</option>
            </select>
          </div>

          <div style="display:flex;justify-content:flex-end;gap:12px;margin-top:20px">
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
const flowerOptions = ref([])
const loading = ref(true)
const modalVisible = ref(false)
const editId = ref(null)
const deletingId = ref(null)
const pendingImageFile = ref(null)
const imagePreview = ref('')
const fileInputRef = ref(null)

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

function createEmptyBomItem() {
  return { flowerId: '', dosage: 1 }
}

function createEmptyForm() {
  return {
    title: '',
    type: 'BOUQUET',
    category: '',
    basePrice: 0,
    packagingFee: 0,
    deliveryFee: 0,
    coverImage: '',
    description: '',
    bomItems: [createEmptyBomItem()],
    status: 'ON_SALE'
  }
}

const form = ref(createEmptyForm())

function formatProductType(type) {
  return PRODUCT_TYPE_LABEL_MAP[type] || type || '-'
}

function formatProductCategory(category) {
  return PRODUCT_CATEGORY_LABEL_MAP[category] || category || '-'
}

function getFlowerName(flowerId) {
  const flower = flowerOptions.value.find(item => String(item.id) === String(flowerId))
  return flower?.name || `花材#${flowerId}`
}

function normalizeBomItems(items = []) {
  if (!Array.isArray(items) || items.length === 0) {
    return [createEmptyBomItem()]
  }
  return items.map(item => ({
    flowerId: item?.flowerId != null ? String(item.flowerId) : '',
    dosage: item?.dosage ?? 1
  }))
}

function buildBomPayload(items = []) {
  return (Array.isArray(items) ? items : [])
    .map(item => ({
      flowerId: String(item?.flowerId || '').trim(),
      dosage: Number(item?.dosage)
    }))
    .filter(item => item.flowerId && Number.isFinite(item.dosage) && item.dosage > 0)
    .map(item => ({
      flowerId: Number(item.flowerId),
      dosage: item.dosage
    }))
}

function formatBomItemsSummary(items, fallbackSummary = '') {
  const payload = buildBomPayload(items)
  if (payload.length > 0) {
    return payload.map(item => `${getFlowerName(item.flowerId)} x${item.dosage}`).join('、')
  }
  return fallbackSummary && String(fallbackSummary).trim() ? fallbackSummary : '未配置花材'
}

async function loadProducts() {
  const res = await api.get('/products')
  if (res.success && Array.isArray(res.data)) {
    allProducts.value = res.data
  } else {
    allProducts.value = []
  }
}

async function loadFlowers() {
  const res = await api.get('/flowers')
  if (res.success && Array.isArray(res.data)) {
    flowerOptions.value = res.data
  } else {
    flowerOptions.value = []
  }
}

async function load() {
  loading.value = true
  try {
    await Promise.all([loadProducts(), loadFlowers()])
  } catch (error) {
    console.error(error)
    showToast('加载商品失败')
  } finally {
    loading.value = false
  }
}

async function ensureFlowerOptions() {
  if (flowerOptions.value.length > 0) return
  await loadFlowers()
}

function syncImagePreview() {
  imagePreview.value = form.value.coverImage ? resolveImageUrl(form.value.coverImage) : ''
}

function resetImagePicker() {
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

function triggerImagePicker() {
  fileInputRef.value?.click()
}

function previewImage(event) {
  const file = event.target.files[0]
  pendingImageFile.value = file || null
  if (!file) {
    syncImagePreview()
    return
  }
  const reader = new FileReader()
  reader.onload = loadEvent => {
    imagePreview.value = loadEvent.target?.result || ''
  }
  reader.readAsDataURL(file)
}

async function showAddForm() {
  await ensureFlowerOptions()
  editId.value = null
  form.value = createEmptyForm()
  pendingImageFile.value = null
  imagePreview.value = ''
  resetImagePicker()
  modalVisible.value = true
}

async function editProduct(id) {
  await ensureFlowerOptions()
  const product = allProducts.value.find(item => item.id === id)
  if (!product) return

  let detail = null
  try {
    const res = await api.get('/products/' + id)
    if (res.success && res.data) {
      detail = res.data
    }
  } catch (error) {
    console.error(error)
  }

  const source = detail || product
  editId.value = id
  form.value = {
    title: source.title || '',
    type: source.type || 'BOUQUET',
    category: source.category || '',
    basePrice: source.basePrice ?? 0,
    packagingFee: source.packagingFee ?? 0,
    deliveryFee: source.deliveryFee ?? 0,
    coverImage: source.coverImage || '',
    description: source.description || '',
    bomItems: normalizeBomItems(source.bomItems),
    status: source.status || 'ON_SALE'
  }
  pendingImageFile.value = null
  syncImagePreview()
  resetImagePicker()
  modalVisible.value = true
}

function addBomItem() {
  form.value.bomItems.push(createEmptyBomItem())
}

function removeBomItem(index) {
  form.value.bomItems.splice(index, 1)
  if (form.value.bomItems.length === 0) {
    form.value.bomItems.push(createEmptyBomItem())
  }
}

async function saveProduct() {
  let coverImage = form.value.coverImage
  if (pendingImageFile.value) {
    try {
      const uploadRes = await api.upload(pendingImageFile.value)
      if (uploadRes.success && uploadRes.data) {
        coverImage = uploadRes.data
      } else {
        showToast('图片上传失败: ' + (uploadRes.message || '未知错误'))
        return
      }
    } catch (error) {
      showToast('图片上传失败: ' + error.message)
      return
    }
  }

  const bomItems = buildBomPayload(form.value.bomItems)
  if (bomItems.length === 0) {
    showToast('请至少配置一种花材')
    return
  }

  const body = {
    title: form.value.title,
    type: form.value.type,
    category: form.value.category,
    basePrice: form.value.basePrice,
    packagingFee: form.value.packagingFee,
    deliveryFee: form.value.deliveryFee,
    coverImage,
    description: form.value.description,
    bomItems
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
    await loadProducts()
  } catch (error) {
    showToast('保存失败: ' + error.message)
  }
}

async function deleteProduct(id) {
  const product = allProducts.value.find(item => item.id === id)
  const title = product?.title || `#${id}`
  if (!confirm(`确认删除商品“${title}”吗？删除后不可恢复。`)) return

  deletingId.value = id
  try {
    const res = await api.del('/products/' + id)
    if (res?.success === false) {
      showToast('删除失败: ' + (res.message || '未知错误'))
      return
    }
    if (editId.value === id) {
      closeModal()
    }
    showToast('删除成功')
    await loadProducts()
  } catch (error) {
    showToast('删除失败: ' + error.message)
  } finally {
    deletingId.value = null
  }
}

function closeModal() {
  modalVisible.value = false
  pendingImageFile.value = null
  resetImagePicker()
}

onMounted(load)
</script>
