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
              <tr>
                <th>ID</th>
                <th>图片</th>
                <th>名称</th>
                <th>分类</th>
                <th>单位</th>
                <th>售价</th>
                <th>成本</th>
                <th>花期(天)</th>
                <th>预警阈值</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading"><td colspan="11" class="loading">加载中...</td></tr>
              <tr v-else-if="allFlowers.length === 0"><td colspan="11" class="empty">暂无花材</td></tr>
              <tr v-for="flower in allFlowers" :key="flower.id" v-else>
                <td>{{ flower.id }}</td>
                <td>
                  <img
                    v-if="flower.image_url"
                    :src="resolveImageUrl(flower.image_url)"
                    style="width:40px;height:40px;object-fit:cover;border-radius:6px"
                  >
                  <span v-else style="display:inline-block;width:40px;height:40px;background:#f5eef2;border-radius:6px"></span>
                </td>
                <td>{{ flower.name }}</td>
                <td><span class="badge badge-info">{{ formatFlowerCategory(flower.category) }}</span></td>
                <td>{{ formatFlowerUnit(flower.unit) }}</td>
                <td style="color:var(--primary)">&yen;{{ formatPrice(flower.sale_price) }}</td>
                <td>&yen;{{ formatPrice(flower.cost_price) }}</td>
                <td>{{ flower.shelf_life_days }}</td>
                <td>{{ flower.warn_threshold }}</td>
                <td>
                  <span class="badge" :class="flower.enabled ? 'badge-success' : 'badge-default'">
                    {{ flower.enabled ? '启用' : '禁用' }}
                  </span>
                </td>
                <td style="white-space:nowrap">
                  <button class="btn btn-sm btn-outline" @click="editFlower(flower.id)">编辑</button>
                  <button
                    v-if="flower.enabled"
                    class="btn btn-sm btn-outline"
                    style="color:var(--warning);border-color:var(--warning)"
                    @click="toggleFlower(flower.id, false)"
                  >禁用</button>
                  <button
                    v-else
                    class="btn btn-sm btn-outline"
                    style="color:var(--success);border-color:var(--success)"
                    @click="toggleFlower(flower.id, true)"
                  >启用</button>
                  <button
                    class="btn btn-sm btn-danger"
                    :disabled="deletingId === flower.id"
                    @click="deleteFlower(flower.id)"
                  >{{ deletingId === flower.id ? '删除中...' : '删除' }}</button>
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
        <h2>{{ editId ? '编辑花材' : '新增花材' }}</h2>
        <form @submit.prevent="saveFlower">
          <div class="form-row">
            <div class="form-group"><label>名称</label><input class="form-control" v-model="form.name" required></div>
            <div class="form-group"><label>分类</label><input class="form-control" v-model="form.category" placeholder="玫瑰 / 百合 / 配材"></div>
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
            <input ref="fileInputRef" type="file" accept="image/*" style="display:none" @change="previewFlowerImage">
            <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
              <button type="button" class="btn btn-outline" @click="triggerImagePicker">{{ pendingFlowerImage ? '重新选择图片' : (imagePreview ? '更换图片' : '上传图片') }}</button>
              <span style="font-size:13px;color:#7a6b75">{{ pendingFlowerImage ? pendingFlowerImage.name : (imagePreview ? '当前已存在花材图片，不更换则继续使用原图' : '未选择文件') }}</span>
            </div>
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
const deletingId = ref(null)
const pendingFlowerImage = ref(null)
const imagePreview = ref('')
const fileInputRef = ref(null)
const form = ref({ name: '', category: '', unit: '枝', salePrice: 0, costPrice: 0, shelfLifeDays: 7, warnThreshold: 10, image: '' })

const FLOWER_CATEGORY_LABEL_MAP = {
  ROSE: '玫瑰',
  LILY: '百合',
  CARNATION: '康乃馨',
  TULIP: '郁金香',
  PACKAGING: '包装耗材',
  ACCESSORY: '配材'
}

const FLOWER_UNIT_LABEL_MAP = {
  stem: '枝',
  sheet: '片',
  meter: '米',
  piece: '个',
  bunch: '束',
  box: '盒',
  kg: '千克'
}

function formatFlowerCategory(category) {
  const key = (category || '').toString().toUpperCase()
  return FLOWER_CATEGORY_LABEL_MAP[key] || category || '-'
}

function formatFlowerUnit(unit) {
  const key = (unit || '').toString().toLowerCase()
  return FLOWER_UNIT_LABEL_MAP[key] || unit || '-'
}

async function load() {
  loading.value = true
  try {
    const res = await api.get('/flowers')
    if (res.success && res.data) allFlowers.value = res.data
  } catch (error) {
    console.error(error)
  }
  loading.value = false
}

function syncImagePreview() {
  imagePreview.value = form.value.image ? resolveImageUrl(form.value.image) : ''
}

function resetImagePicker() {
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

function triggerImagePicker() {
  fileInputRef.value?.click()
}

function previewFlowerImage(event) {
  const file = event.target.files[0]
  pendingFlowerImage.value = file || null
  if (!file) {
    syncImagePreview()
    return
  }
  const reader = new FileReader()
  reader.onload = current => { imagePreview.value = current.target.result }
  reader.readAsDataURL(file)
}

async function toggleFlower(id, enabled) {
  const flower = allFlowers.value.find(item => item.id === id)
  if (!flower) return
  try {
    await api.put('/flowers/' + id, {
      name: flower.name,
      category: flower.category,
      unit: flower.unit,
      salePrice: flower.sale_price,
      costPrice: flower.cost_price,
      shelfLifeDays: flower.shelf_life_days,
      warnThreshold: flower.warn_threshold,
      enabled,
      image: flower.image_url || ''
    })
    showToast(enabled ? '已启用' : '已禁用')
    load()
  } catch {
    showToast('操作失败')
  }
}


async function deleteFlower(id) {
  const flower = allFlowers.value.find(item => item.id === id)
  const name = flower?.name || `#${id}`
  if (!window.confirm(`确认删除花材“${name}”吗？删除后不可恢复。`)) return

  deletingId.value = id
  try {
    const res = await api.del('/flowers/' + id)
    if (res?.success === false) {
      showToast('删除失败: ' + (res.message || '未知错误'))
      return
    }
    if (editId.value === id) {
      closeModal()
    }
    showToast('删除成功')
    await load()
  } catch (error) {
    showToast('删除失败: ' + error.message)
  } finally {
    deletingId.value = null
  }
}
function showAddForm() {
  editId.value = null
  form.value = { name: '', category: '', unit: '枝', salePrice: 0, costPrice: 0, shelfLifeDays: 7, warnThreshold: 10, image: '' }
  pendingFlowerImage.value = null
  imagePreview.value = ''
  resetImagePicker()
  modalVisible.value = true
}

function editFlower(id) {
  const flower = allFlowers.value.find(item => item.id === id)
  if (!flower) return
  editId.value = id
  form.value = {
    name: flower.name || '',
    category: flower.category || '',
    unit: flower.unit || '枝',
    salePrice: flower.sale_price ?? 0,
    costPrice: flower.cost_price ?? 0,
    shelfLifeDays: flower.shelf_life_days ?? 7,
    warnThreshold: flower.warn_threshold ?? 10,
    image: flower.image_url || ''
  }
  pendingFlowerImage.value = null
  syncImagePreview()
  resetImagePicker()
  modalVisible.value = true
}

async function saveFlower() {
  let image = form.value.image
  if (pendingFlowerImage.value) {
    try {
      const uploadRes = await api.upload(pendingFlowerImage.value)
      if (uploadRes.success && uploadRes.data) {
        image = uploadRes.data
      } else {
        showToast('图片上传失败: ' + (uploadRes.message || '未知错误'))
        return
      }
    } catch (error) {
      showToast('图片上传失败: ' + error.message)
      return
    }
  }

  const body = {
    name: form.value.name,
    category: form.value.category,
    unit: form.value.unit,
    salePrice: Number.parseFloat(form.value.salePrice) || 0,
    costPrice: Number.parseFloat(form.value.costPrice) || 0,
    shelfLifeDays: Number.parseInt(form.value.shelfLifeDays, 10) || 0,
    warnThreshold: Number.parseInt(form.value.warnThreshold, 10) || 0,
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
  } catch (error) {
    showToast('保存失败: ' + error.message)
  }
}

function closeModal() {
  modalVisible.value = false
  pendingFlowerImage.value = null
  resetImagePicker()
}

onMounted(load)
</script>