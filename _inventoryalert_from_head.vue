<template>
  <div>
    <div class="top-bar">
      <h1 class="page-title">库存预警</h1>
      <div class="top-bar-actions">
        <button class="btn btn-outline" @click="exportAlertList">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="toolbar-icon">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="7 10 12 15 17 10"/>
            <line x1="12" y1="15" x2="12" y2="3"/>
          </svg>
          导出预警列表
        </button>
        <button class="btn btn-primary" @click="showThresholdSettings">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="toolbar-icon">
            <circle cx="12" cy="12" r="3"/>
            <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
          </svg>
          预警设置
        </button>
      </div>
    </div>

    <!-- 库存概览卡片 -->
    <div class="stats-grid">
      <div class="stat-card warning">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
            <line x1="12" y1="9" x2="12" y2="13"/>
            <line x1="12" y1="17" x2="12.01" y2="17"/>
          </svg>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.lowStockCount }}</div>
          <div class="stat-label">低库存商品</div>
        </div>
      </div>
      <div class="stat-card danger">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="8" x2="12" y2="12"/>
            <line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.outOfStockCount }}</div>
          <div class="stat-label">缺货商品</div>
        </div>
      </div>
      <div class="stat-card info">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
          </svg>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.alertCount }}</div>
          <div class="stat-label">预警商品</div>
        </div>
      </div>
      <div class="stat-card success">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 2L2 7l10 5 10-5-10-5z"/>
            <path d="M2 17l10 5 10-5"/>
            <path d="M2 12l10 5 10-5"/>
          </svg>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.totalRestockNeeded }}</div>
          <div class="stat-label">建议补货总量</div>
        </div>
      </div>
    </div>

    <!-- FEFO批次管理提示 -->
    <div class="alert-bar info" v-if="fefoTips.length > 0">
      <strong>FEFO提醒：</strong>以下批次即将过期，建议优先使用：
      <span v-for="(tip, index) in fefoTips" :key="index" class="fefo-item">
        {{ tip.flowerName }}(批次{{ tip.batchId }} - 剩余{{ tip.daysLeft }}天)
      </span>
    </div>

    <!-- 低库存商品列表 -->
    <div class="card">
      <div class="card-header">
        <span class="card-title">库存商品列表</span>
        <div class="filter-bar">
          <select class="form-control" v-model="filterStatus" @change="filterProducts">
            <option value="">全部状态</option>
            <option value="NORMAL">正常</option>
            <option value="WARNING">预警</option>
            <option value="OUT_OF_STOCK">缺货</option>
          </select>
          <input type="text" class="form-control" v-model="searchKeyword" placeholder="搜索商品名称" @input="filterProducts"/>
        </div>
      </div>
      <div class="card-body">
        <div class="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>商品名称</th>
                <th>当前库存</th>
                <th>预警阈值</th>
                <th>建议补货量</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading"><td colspan="6" class="loading">加载中...</td></tr>
              <tr v-else-if="filteredProducts.length === 0"><td colspan="6" class="empty">暂无库存商品</td></tr>
              <tr v-for="product in filteredProducts" :key="product.productId" v-else>
                <td>
                  <div class="product-name">{{ product.productName }}</div>
                  <div class="product-sub" v-if="product.flowerName">{{ product.flowerName }}</div>
                </td>
                <td :class="{ 'text-danger': product.currentStock <= 0, 'text-warning': product.currentStock > 0 && product.currentStock <= product.warnThreshold }">
                  {{ product.currentStock }}
                </td>
                <td>{{ product.warnThreshold }}</td>
                <td>
                  <span class="restock-suggestion" v-if="product.suggestedRestock > 0">
                    +{{ product.suggestedRestock }}
                  </span>
                  <span v-else>-</span>
                </td>
                <td>
                  <span class="status-tag" :class="getStatusClass(product.status)">
                    {{ getStatusText(product.status) }}
                  </span>
                </td>
                <td>
                  <div class="action-btns">
                    <button class="btn btn-sm btn-primary" @click="quickRestock(product)" v-if="product.status !== 'NORMAL'">
                      快速补货
                    </button>
                    <button class="btn btn-sm btn-outline" @click="showAdjustModal(product)">
                      调整库存
                    </button>
                    <button class="btn btn-sm btn-outline" @click="toggleBatches(product)">
                      {{ expandedProduct === product.productId ? '收起' : '查看批次' }}
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 批次库存展开详情 -->
        <div v-if="expandedProduct" class="batch-detail">
          <div class="batch-detail-header">
            <h4>批次库存详情 - {{ getProductName(expandedProduct) }}</h4>
            <span class="fefo-badge">FEFO 先进先出</span>
          </div>
          <div class="table-wrapper">
            <table class="batch-table">
              <thead>
                <tr>
                  <th>批次ID</th>
                  <th>入库时间</th>
                  <th>过期时间</th>
                  <th>剩余天数</th>
                  <th>当前库存</th>
                  <th>锁定库存</th>
                  <th>可用库存</th>
                  <th>品质</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="batchLoading"><td colspan="9" class="loading">加载中...</td></tr>
                <tr v-else-if="productBatches.length === 0"><td colspan="9" class="empty">暂无批次数据</td></tr>
                <tr v-for="batch in productBatches" :key="batch.batchId" :class="{ 'expiring-soon': batch.daysLeft <= 2 }">
                  <td>#{{ batch.batchId }}</td>
                  <td>{{ formatDate(batch.receiptTime) }}</td>
                  <td>{{ formatDate(batch.wiltTime) }}</td>
                  <td>
                    <span :class="{ 'text-danger': batch.daysLeft <= 2, 'text-warning': batch.daysLeft <= 5 && batch.daysLeft > 2 }">
                      {{ batch.daysLeft }}天
                    </span>
                  </td>
                  <td>{{ batch.currentQty }}</td>
                  <td>{{ batch.lockedQty }}</td>
                  <td class="batch-available">{{ batch.availableQty }}</td>
                  <td>
                    <span class="quality-badge" :class="'quality-' + batch.qualityStatus">
                      {{ batch.qualityStatus }}
                    </span>
                  </td>
                  <td>
                    <button class="btn btn-sm btn-outline" @click="editBatch(batch)">编辑</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>

    <!-- 库存调整弹窗 -->
    <div v-if="adjustModalVisible" class="modal">
      <div class="modal-overlay" @click="adjustModalVisible = false"></div>
      <div class="modal-content">
        <h2>库存调整</h2>
        <form @submit.prevent="saveAdjustment">
          <div class="form-group">
            <label>商品名称</label>
            <input type="text" class="form-control" :value="adjustForm.productName" disabled/>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>当前库存</label>
              <input type="number" class="form-control" :value="adjustForm.currentStock" disabled/>
            </div>
            <div class="form-group">
              <label>调整后库存</label>
              <input type="number" class="form-control" v-model="adjustForm.newStock" min="0" required/>
            </div>
          </div>
          <div class="form-group">
            <label>调整原因</label>
            <select class="form-control" v-model="adjustForm.reason" required>
              <option value="">请选择原因</option>
              <option value="DAMAGE">商品损坏</option>
              <option value="THEFT">丢失/被盗</option>
              <option value="COUNT_ERROR">盘点差异</option>
              <option value="EXPIRED">过期报废</option>
              <option value="OTHER">其他</option>
            </select>
          </div>
          <div class="form-group">
            <label>备注</label>
            <textarea class="form-control" v-model="adjustForm.remark" rows="2" placeholder="请输入备注信息"></textarea>
          </div>
          <div class="form-actions">
            <button type="button" class="btn btn-outline" @click="adjustModalVisible = false">取消</button>
            <button type="submit" class="btn btn-primary">确认调整</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 批次编辑弹窗 -->
    <div v-if="batchModalVisible" class="modal">
      <div class="modal-overlay" @click="batchModalVisible = false"></div>
      <div class="modal-content">
        <h2>编辑批次库存</h2>
        <form @submit.prevent="saveBatchUpdate">
          <div class="form-group">
            <label>批次ID</label>
            <input type="text" class="form-control" :value="'#' + batchForm.batchId" disabled/>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>当前库存</label>
              <input type="number" class="form-control" :value="batchForm.currentQty" disabled/>
            </div>
            <div class="form-group">
              <label>新库存</label>
              <input type="number" class="form-control" v-model="batchForm.newQty" min="0" required/>
            </div>
          </div>
          <div class="form-group">
            <label>过期时间</label>
            <input type="datetime-local" class="form-control" v-model="batchForm.wiltTime" required/>
          </div>
          <div class="form-group">
            <label>品质状态</label>
            <select class="form-control" v-model="batchForm.qualityStatus" required>
              <option value="A">A - 新鲜</option>
              <option value="B">B - 轻微折损</option>
              <option value="C">C - 临期促销</option>
            </select>
          </div>
          <div class="form-actions">
            <button type="button" class="btn btn-outline" @click="batchModalVisible = false">取消</button>
            <button type="submit" class="btn btn-primary">保存</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 预警设置弹窗 -->
    <div v-if="settingsModalVisible" class="modal">
      <div class="modal-overlay" @click="settingsModalVisible = false"></div>
      <div class="modal-content">
        <h2>预警阈值设置</h2>
        <form @submit.prevent="saveSettings">
          <div class="form-group">
            <label>全局默认预警阈值</label>
            <input type="number" class="form-control" v-model="settingsForm.defaultThreshold" min="1" required/>
            <span class="form-hint">当库存低于此值时触发预警</span>
          </div>
          <div class="form-group">
            <label>缺货阈值</label>
            <input type="number" class="form-control" v-model="settingsForm.outOfStockThreshold" min="0" required/>
            <span class="form-hint">当库存低于或等于此值时标记为缺货</span>
          </div>
          <div class="form-group">
            <label>建议补货倍数</label>
            <input type="number" class="form-control" v-model="settingsForm.restockMultiplier" min="1" step="0.5" required/>
            <span class="form-hint">建议补货量 = 预警阈值 × 此倍数</span>
          </div>
          <div class="form-actions">
            <button type="button" class="btn btn-outline" @click="settingsModalVisible = false">取消</button>
            <button type="submit" class="btn btn-primary">保存设置</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 快速补货弹窗 -->
    <div v-if="restockModalVisible" class="modal">
      <div class="modal-overlay" @click="restockModalVisible = false"></div>
      <div class="modal-content">
        <h2>创建补货单</h2>
        <form @submit.prevent="saveRestock">
          <div class="form-group">
            <label>商品名称</label>
            <input type="text" class="form-control" :value="restockForm.productName" disabled/>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>当前库存</label>
              <input type="number" class="form-control" :value="restockForm.currentStock" disabled/>
            </div>
            <div class="form-group">
              <label>补货数量</label>
              <input type="number" class="form-control" v-model="restockForm.quantity" min="1" required/>
            </div>
          </div>
          <div class="form-group">
            <label>供应商</label>
            <input type="text" class="form-control" v-model="restockForm.supplierName" placeholder="请输入供应商名称"/>
          </div>
          <div class="form-group">
            <label>预计到货时间</label>
            <input type="datetime-local" class="form-control" v-model="restockForm.expectedArrival" :min="restockMinDatetime" required/>
          </div>
          <div class="form-group">
            <label>备注</label>
            <textarea class="form-control" v-model="restockForm.remark" rows="2" placeholder="请输入备注信息"></textarea>
          </div>
          <div class="form-actions">
            <button type="button" class="btn btn-outline" @click="restockModalVisible = false">取消</button>
            <button type="submit" class="btn btn-primary">创建补货单</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../api'
import { showToast } from '../../utils'

const router = useRouter()

// 数据状态
const loading = ref(false)
const batchLoading = ref(false)
const products = ref([])
const filteredProducts = ref([])
const productBatches = ref([])
const expandedProduct = ref(null)
const fefoTips = ref([])

// 筛选状态
const filterStatus = ref('')
const searchKeyword = ref('')

// 统计数据
const stats = ref({
  lowStockCount: 0,
  outOfStockCount: 0,
  alertCount: 0,
  totalRestockNeeded: 0
})

// 弹窗状态
const adjustModalVisible = ref(false)
const batchModalVisible = ref(false)
const settingsModalVisible = ref(false)
const restockModalVisible = ref(false)

// 表单数据
const adjustForm = ref({
  productId: null,
  productName: '',
  currentStock: 0,
  newStock: 0,
  reason: '',
  remark: ''
})

const batchForm = ref({
  batchId: null,
  currentQty: 0,
  newQty: 0,
  wiltTime: '',
  qualityStatus: 'A'
})

const settingsForm = ref({
  defaultThreshold: 10,
  outOfStockThreshold: 0,
  restockMultiplier: 2
})

const restockForm = ref({
  productId: null,
  productName: '',
  currentStock: 0,
  quantity: 0,
  supplierName: '',
  expectedArrival: '',
  remark: ''
})
const restockMinDatetime = ref('')

function formatDatetimeLocal(date) {
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function refreshRestockMinDatetime() {
  restockMinDatetime.value = formatDatetimeLocal(new Date())
}

// 获取低库存商品列表
async function loadLowStockProducts() {
  loading.value = true
  try {
    const flowersRes = await api.get('/flowers')
    if (!flowersRes.success || !Array.isArray(flowersRes.data)) {
      showToast('加载花材失败')
      products.value = []
      filteredProducts.value = []
      updateStats()
      return
    }

    const rows = await Promise.all(
      flowersRes.data.map(async (flower) => {
        let batches = []
        try {
          const fefoRes = await api.get(`/inventory/fefo/${flower.id}`)
          if (fefoRes.success && Array.isArray(fefoRes.data)) {
            batches = fefoRes.data
          }
        } catch (e) {
          batches = []
        }

        const availableStock = batches.reduce((sum, batch) => {
          const value = Number(batch.availableQty ?? (Number(batch.currentQty || 0) - Number(batch.lockedQty || 0)))
          return sum + (Number.isFinite(value) ? value : 0)
        }, 0)

        const warnThreshold = Number(flower.warn_threshold ?? flower.warnThreshold ?? 0)
        const status = availableStock <= 0
          ? 'OUT_OF_STOCK'
          : availableStock <= warnThreshold
            ? 'WARNING'
            : 'NORMAL'

        return {
          productId: flower.id,
          productName: flower.name || `花材#${flower.id}`,
          flowerName: flower.name || '',
          currentStock: availableStock,
          warnThreshold,
          suggestedRestock: status === 'NORMAL' ? 0 : Math.max(warnThreshold - availableStock, 0),
          status
        }
      })
    )

    const rank = { OUT_OF_STOCK: 0, WARNING: 1, NORMAL: 2 }
    products.value = rows.sort((a, b) => {
      const delta = (rank[a.status] ?? 9) - (rank[b.status] ?? 9)
      if (delta !== 0) return delta
      return a.currentStock - b.currentStock
    })

    filteredProducts.value = [...products.value]
    updateStats()
  } catch (e) {
    console.error('load inventory failed', e)
    showToast('加载失败，请重试')
    products.value = []
    filteredProducts.value = []
    updateStats()
  } finally {
    loading.value = false
  }
}
function updateStats() {
  stats.value.lowStockCount = products.value.filter(p => p.status === 'WARNING').length
  stats.value.outOfStockCount = products.value.filter(p => p.status === 'OUT_OF_STOCK').length
  stats.value.alertCount = products.value.filter(p => p.status !== 'NORMAL').length
  stats.value.totalRestockNeeded = products.value.reduce((sum, p) => sum + (p.suggestedRestock || 0), 0)
}

// 筛选商品
function filterProducts() {
  let result = [...products.value]
  if (filterStatus.value) {
    result = result.filter(p => p.status === filterStatus.value)
  }
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(p =>
      p.productName?.toLowerCase().includes(keyword) ||
      p.flowerName?.toLowerCase().includes(keyword)
    )
  }
  filteredProducts.value = result
}

// 获取状态样式
function getStatusClass(status) {
  const map = {
    'NORMAL': 'status-normal',
    'WARNING': 'status-warning',
    'OUT_OF_STOCK': 'status-danger'
  }
  return map[status] || 'status-normal'
}

// 获取状态文本
function getStatusText(status) {
  const map = {
    'NORMAL': '正常',
    'WARNING': '预警',
    'OUT_OF_STOCK': '缺货'
  }
  return map[status] || status
}

// 获取商品名称
function getProductName(productId) {
  const product = products.value.find(p => p.productId === productId)
  return product?.productName || '未知商品'
}

// 切换批次展开
async function toggleBatches(product) {
  if (expandedProduct.value === product.productId) {
    expandedProduct.value = null
    productBatches.value = []
    return
  }
  expandedProduct.value = product.productId
  await loadProductBatches(product.productId)
}

// 加载商品批次详情
async function loadProductBatches(productId) {
  batchLoading.value = true
  try {
    const res = await api.get(`/inventory/fefo/${productId}`)
    if (res.success) {
      const rows = Array.isArray(res.data) ? res.data : []
      productBatches.value = rows.map(batch => ({
        ...batch,
        daysLeft: calculateDaysLeft(batch.wiltTime)
      }))
    }
  } catch (e) {
    console.error('加载批次详情失败:', e)
    showToast('加载批次详情失败')
  }
  batchLoading.value = false
}

// 计算剩余天数
function calculateDaysLeft(wiltTime) {
  if (!wiltTime) return 0
  const end = new Date(wiltTime)
  const now = new Date()
  const diff = Math.ceil((end - now) / (1000 * 60 * 60 * 24))
  return Math.max(0, diff)
}

// 格式化日期
function formatDate(dateStr) {
  if (!dateStr) return '-'
  return dateStr.replace('T', ' ').substring(0, 16)
}

// 显示库存调整弹窗
function showAdjustModal(product) {
  adjustForm.value = {
    productId: product.productId,
    productName: product.productName,
    currentStock: product.currentStock,
    newStock: product.currentStock,
    reason: '',
    remark: ''
  }
  adjustModalVisible.value = true
}

// 保存库存调整
async function saveAdjustment() {
  const targetStock = parseInt(adjustForm.value.newStock)
  const currentStock = parseInt(adjustForm.value.currentStock)
  if (!Number.isFinite(targetStock) || targetStock < 0) {
    showToast('请输入正确的库存数量')
    return
  }

  const delta = targetStock - currentStock
  if (delta <= 0) {
    showToast('当前版本仅支持补库存，请输入大于当前库存的数量')
    return
  }

  try {
    const res = await api.post('/inventory/batch', {
      flowerId: adjustForm.value.productId,
      quantity: delta,
      shelfLifeDays: 7,
      supplierName: adjustForm.value.reason || '库存调整',
      qualityStatus: 'A'
    })
    if (res.success) {
      showToast('库存调整成功')
      adjustModalVisible.value = false
      loadLowStockProducts()
    } else {
      showToast('调整失败: ' + (res.message || ''))
    }
  } catch (e) {
    showToast('调整失败: ' + e.message)
  }
}

// 编辑批次
function editBatch(batch) {
  batchForm.value = {
    batchId: batch.batchId,
    currentQty: batch.currentQty,
    newQty: batch.currentQty,
    wiltTime: batch.wiltTime ? batch.wiltTime.substring(0, 16) : '',
    qualityStatus: batch.qualityStatus
  }
  batchModalVisible.value = true
}

// 保存批次更新
async function saveBatchUpdate() {
  showToast('当前后端暂不支持直接编辑批次，请使用快速补货')
  batchModalVisible.value = false
}

// 显示预警设置
function showThresholdSettings() {
  settingsModalVisible.value = true
}

// 保存预警设置
async function saveSettings() {
  showToast('当前后端暂不支持在线修改预警配置')
  settingsModalVisible.value = false
}

// 快速补货
function quickRestock(product) {
  restockForm.value = {
    productId: product.productId,
    productName: product.productName,
    currentStock: product.currentStock,
    quantity: product.suggestedRestock || product.warnThreshold * 2,
    supplierName: '',
    expectedArrival: '',
    remark: ''
  }
  refreshRestockMinDatetime()
  // 设置默认到货时间为明天
  const tomorrow = new Date()
  tomorrow.setDate(tomorrow.getDate() + 1)
  tomorrow.setHours(10, 0, 0, 0)
  const defaultArrival = formatDatetimeLocal(tomorrow)
  restockForm.value.expectedArrival = defaultArrival < restockMinDatetime.value ? restockMinDatetime.value : defaultArrival
  restockModalVisible.value = true
}

// 保存补货单
async function saveRestock() {
  const quantity = parseInt(restockForm.value.quantity)
  if (!Number.isFinite(quantity) || quantity <= 0) {
    showToast('请输入正确的补货数量')
    return
  }

  const expectedArrival = restockForm.value.expectedArrival
  if (!expectedArrival) {
    showToast('请选择预计到货时间')
    return
  }

  refreshRestockMinDatetime()
  if (expectedArrival < restockMinDatetime.value) {
    showToast('补货时间不能早于当前时间')
    return
  }

  const expectedArrivalDate = new Date(expectedArrival)
  if (Number.isNaN(expectedArrivalDate.getTime())) {
    showToast('预计到货时间格式不正确')
    return
  }

  const days = expectedArrival
    ? Math.max(1, Math.ceil((expectedArrivalDate.getTime() - Date.now()) / (1000 * 60 * 60 * 24)))
    : 7

  try {
    const res = await api.post('/inventory/batch', {
      flowerId: restockForm.value.productId,
      quantity,
      shelfLifeDays: days,
      supplierName: restockForm.value.supplierName || '默认供应商',
      qualityStatus: 'A'
    })
    if (res.success) {
      showToast('补货单创建成功')
      restockModalVisible.value = false
      loadLowStockProducts()
    } else {
      showToast('创建失败: ' + (res.message || ''))
    }
  } catch (e) {
    showToast('创建失败: ' + e.message)
  }
}

// 导出预警列表
function exportAlertList() {
  const csvContent = [
    ['商品名称', '花材名称', '当前库存', '预警阈值', '建议补货量', '状态'].join(','),
    ...filteredProducts.value.map(p => [
      p.productName,
      p.flowerName || '',
      p.currentStock,
      p.warnThreshold,
      p.suggestedRestock || 0,
      getStatusText(p.status)
    ].join(','))
  ].join('\n')

  const blob = new Blob(['\ufeff' + csvContent], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `库存预警列表_${new Date().toISOString().split('T')[0]}.csv`
  link.click()
  showToast('导出成功')
}

// 加载FEFO提示
async function loadFefoTips() {
  // 从批次数据中筛选即将过期的
  const tips = []
  for (const product of products.value.slice(0, 5)) {
    try {
      const res = await api.get(`/inventory/fefo/${product.productId}`)
      const batches = Array.isArray(res.data) ? res.data : []
      if (res.success) {
        for (const batch of batches) {
          const daysLeft = calculateDaysLeft(batch.wiltTime)
          if (daysLeft <= 3 && batch.availableQty > 0) {
            tips.push({
              flowerName: product.flowerName || product.productName,
              batchId: batch.batchId,
              daysLeft
            })
          }
        }
      }
    } catch (e) {
      // 忽略单个商品的错误
    }
  }
  fefoTips.value = tips.slice(0, 5)
}

onMounted(() => {
  refreshRestockMinDatetime()
  loadLowStockProducts()
  // 延迟加载FEFO提示
  setTimeout(loadFefoTips, 1000)
})
</script>

<style scoped>
.toolbar-icon {
  width: 16px;
  height: 16px;
  margin-right: 4px;
  vertical-align: middle;
}

.batch-available {
  font-weight: 600;
}

/* 统计卡片 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  border-left: 4px solid;
}

.stat-card.warning {
  border-left-color: #f59e0b;
}

.stat-card.danger {
  border-left-color: #ef4444;
}

.stat-card.info {
  border-left-color: #3b82f6;
}

.stat-card.success {
  border-left-color: #10b981;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-card.warning .stat-icon {
  background: #fef3c7;
  color: #f59e0b;
}

.stat-card.danger .stat-icon {
  background: #fee2e2;
  color: #ef4444;
}

.stat-card.info .stat-icon {
  background: #dbeafe;
  color: #3b82f6;
}

.stat-card.success .stat-icon {
  background: #d1fae5;
  color: #10b981;
}

.stat-icon svg {
  width: 24px;
  height: 24px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #1f2937;
  line-height: 1;
}

.stat-label {
  font-size: 14px;
  color: #6b7280;
  margin-top: 4px;
}

/* 顶部栏 */
.top-bar-actions {
  display: flex;
  gap: 12px;
}

/* FEFO提示 */
.fefo-item {
  display: inline-block;
  background: #fef3c7;
  color: #92400e;
  padding: 2px 8px;
  border-radius: 4px;
  margin-left: 8px;
  font-size: 13px;
}

/* 筛选栏 */
.filter-bar {
  display: flex;
  gap: 12px;
}

.filter-bar .form-control {
  width: 140px;
}

.filter-bar input.form-control {
  width: 200px;
}

/* 表格样式 */
.product-name {
  font-weight: 500;
  color: #1f2937;
}

.product-sub {
  font-size: 12px;
  color: #6b7280;
  margin-top: 2px;
}

.restock-suggestion {
  background: #dbeafe;
  color: #1e40af;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
}

.status-tag {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
}

.status-normal {
  background: #d1fae5;
  color: #065f46;
}

.status-warning {
  background: #fef3c7;
  color: #92400e;
}

.status-danger {
  background: #fee2e2;
  color: #991b1b;
}

.action-btns {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.text-danger {
  color: #ef4444;
  font-weight: 600;
}

.text-warning {
  color: #f59e0b;
  font-weight: 600;
}

/* 批次详情 */
.batch-detail {
  margin-top: 20px;
  padding: 20px;
  background: #f9fafb;
  border-radius: 8px;
}

.batch-detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.batch-detail-header h4 {
  margin: 0;
  font-size: 16px;
  color: #1f2937;
}

.fefo-badge {
  background: #ede9fe;
  color: #5b21b6;
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.batch-table {
  font-size: 13px;
}

.batch-table th {
  background: #f3f4f6;
}

.expiring-soon {
  background: #fef2f2;
}

.quality-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.quality-A {
  background: #d1fae5;
  color: #065f46;
}

.quality-B {
  background: #fef3c7;
  color: #92400e;
}

.quality-C {
  background: #fee2e2;
  color: #991b1b;
}

/* 表单提示 */
.form-hint {
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
  display: block;
}

/* 响应式 */
@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .filter-bar {
    flex-direction: column;
  }

  .filter-bar .form-control,
  .filter-bar input.form-control {
    width: 100%;
  }

  .action-btns {
    flex-direction: column;
  }
}
</style>
