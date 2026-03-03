<template>
  <div>
    <div class="top-bar">
      <h1 class="page-title">订单管理</h1>
      <div class="orders-actions">
        <button class="btn btn-outline" @click="loadOrders">刷新</button>
        <button class="btn btn-outline" @click="exportCsv">导出当前筛选</button>
        <button class="btn btn-outline" @click="releaseExpired">释放过期锁单</button>
      </div>
    </div>

    <div class="card">
      <div class="card-body orders-filters">
        <div class="orders-filter-row">
          <span class="orders-label">订单状态：</span>
          <button
            v-for="s in statusFilters"
            :key="s.value"
            class="btn btn-sm"
            :class="currentStatus === s.value ? 'btn-primary' : 'btn-outline'"
            @click="changeStatus(s.value)"
          >
            {{ s.label }}
          </button>
        </div>

        <div class="orders-filter-row">
          <span class="orders-label">创建时间：</span>
          <input v-model="dateRange.start" class="form-control orders-date-input" type="date">
          <span style="color:var(--text-muted)">至</span>
          <input v-model="dateRange.end" class="form-control orders-date-input" type="date">
          <input
            v-model.trim="keyword"
            class="form-control orders-keyword-input"
            placeholder="搜索订单号 / 收货人 / 手机号"
            @keyup.enter="applyFilters"
          >
          <button class="btn btn-primary" @click="applyFilters">搜索</button>
          <button class="btn btn-outline" @click="resetFilters">重置</button>
        </div>
      </div>
    </div>

    <div v-if="selectedOrders.length > 0" class="alert-bar warning orders-batch-bar">
      <span>已选中 {{ selectedOrders.length }} 条订单</span>
      <div class="orders-batch-actions">
        <button class="btn btn-sm btn-success" @click="batchComplete">批量完成</button>
        <button class="btn btn-sm btn-danger" @click="batchCancel">批量取消</button>
        <button class="btn btn-sm btn-outline" @click="clearSelection">清空选择</button>
      </div>
    </div>

    <div class="card">
      <div class="card-header">
        <span class="card-title">订单列表</span>
        <span style="font-size:12px;color:var(--text-muted)">共 {{ filteredOrders.length }} 条</span>
      </div>
      <div class="card-body">
        <div class="table-wrapper">
          <table>
            <thead>
              <tr>
                <th style="width:40px;text-align:center">
                  <input type="checkbox" :checked="isPageAllSelected" @change="toggleSelectPage">
                </th>
                <th>订单号</th>
                <th>收货人</th>
                <th>商品</th>
                <th>金额</th>
                <th>状态</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading"><td colspan="8" class="loading">加载中...</td></tr>
              <tr v-else-if="pagedOrders.length === 0"><td colspan="8" class="empty">暂无订单</td></tr>
              <tr v-for="order in pagedOrders" :key="order.orderNo" v-else>
                <td style="text-align:center">
                  <input type="checkbox" :value="order.orderNo" v-model="selectedOrders">
                </td>
                <td style="font-family:monospace;font-size:12px">{{ order.orderNo }}</td>
                <td>
                  <div>{{ order.receiverName || '-' }}</div>
                  <div style="font-size:12px;color:var(--text-muted)">{{ order.receiverPhone || '' }}</div>
                </td>
                <td class="orders-item-cell">{{ getItemNames(order) }}</td>
                <td style="font-weight:600;color:var(--primary)">¥{{ formatPrice(order.totalAmount) }}</td>
                <td>
                  <span class="badge" :class="statusBadge[order.status] || 'badge-default'">
                    {{ statusMap[order.status] || order.status }}
                  </span>
                </td>
                <td style="font-size:12px;color:var(--text-muted)">{{ formatTime(order.createdAt) }}</td>
                <td style="white-space:nowrap">
                  <button class="btn btn-sm btn-outline" @click="viewDetail(order.orderNo)">详情</button>
                  <button v-if="order.status === 'PAID'" class="btn btn-sm btn-primary" @click="confirmOrder(order.orderNo)">发货</button>
                  <button v-if="order.status === 'CONFIRMED'" class="btn btn-sm btn-success" @click="completeOrder(order.orderNo)">完成</button>
                  <button v-if="canCancel(order.status)" class="btn btn-sm btn-danger" @click="cancelOrder(order.orderNo)">取消</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="filteredOrders.length > 0" class="orders-pagination">
          <div class="orders-page-summary">
            显示 {{ pageStart + 1 }} - {{ pageEnd }} 条
          </div>
          <div class="orders-page-controls">
            <button class="btn btn-sm btn-outline" :disabled="currentPage === 1" @click="goToPage(currentPage - 1)">上一页</button>
            <button class="btn btn-sm btn-outline" :disabled="currentPage >= totalPages" @click="goToPage(currentPage + 1)">下一页</button>
            <select v-model.number="pageSize" class="form-control orders-page-size" @change="handlePageSizeChange">
              <option :value="10">10 / 页</option>
              <option :value="20">20 / 页</option>
              <option :value="50">50 / 页</option>
            </select>
          </div>
        </div>
      </div>
    </div>

    <div v-if="detailVisible" class="modal">
      <div class="modal-overlay" @click="detailVisible = false"></div>
      <div class="modal-content orders-detail-modal">
        <div class="orders-detail-header">
          <h2>订单详情</h2>
          <button class="btn btn-sm btn-outline" @click="detailVisible = false">关闭</button>
        </div>

        <div class="orders-detail-meta">
          <div><strong>订单号：</strong><span style="font-family:monospace">{{ detail.orderNo }}</span></div>
          <div><strong>状态：</strong>{{ statusMap[detail.status] || detail.status }}</div>
          <div><strong>收货人：</strong>{{ detail.receiverName || '-' }} {{ detail.receiverPhone || '' }}</div>
          <div><strong>地址：</strong>{{ detail.receiverAddress || '-' }}</div>
          <div v-if="detail.createdAt"><strong>创建时间：</strong>{{ formatTime(detail.createdAt) }}</div>
        </div>

        <div class="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>商品</th>
                <th style="text-align:right">单价</th>
                <th style="text-align:center">数量</th>
                <th style="text-align:right">小计</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in detail.items || []" :key="(item.productId || 'x') + '-' + (item.productTitle || item.productName || item.title || 'item')">
                <td>{{ resolveOrderItemName(item) }}</td>
                <td style="text-align:right">¥{{ formatPrice(item.unitPrice) }}</td>
                <td style="text-align:center">{{ item.quantity }}</td>
                <td style="text-align:right;font-weight:600">¥{{ formatPrice(item.lineAmount) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="orders-detail-total">总金额：¥{{ formatPrice(detail.totalAmount) }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch, onMounted } from 'vue'
import { api } from '../../api'
import { formatPrice, showToast } from '../../utils'

const loading = ref(false)
const allOrders = ref([])
const filteredOrders = ref([])
const selectedOrders = ref([])

const currentStatus = ref('')
const keyword = ref('')
const dateRange = ref({ start: '', end: '' })

const currentPage = ref(1)
const pageSize = ref(20)

const detailVisible = ref(false)
const detail = ref({})

const statusMap = {
  CREATED: '已创建',
  LOCKED: '待支付',
  PAID: '待发货',
  CONFIRMED: '已发货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REFUNDED: '已退款'
}

const statusBadge = {
  CREATED: 'badge-default',
  LOCKED: 'badge-warning',
  PAID: 'badge-success',
  CONFIRMED: 'badge-info',
  COMPLETED: 'badge-success',
  CANCELLED: 'badge-default',
  REFUNDED: 'badge-warning'
}

const statusFilters = [
  { value: '', label: '全部' },
  { value: 'LOCKED', label: '待支付' },
  { value: 'PAID', label: '待发货' },
  { value: 'CONFIRMED', label: '已发货' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'REFUNDED', label: '已退款' }
]

const totalPages = computed(() => Math.max(1, Math.ceil(filteredOrders.value.length / pageSize.value)))
const pageStart = computed(() => (currentPage.value - 1) * pageSize.value)
const pageEnd = computed(() => Math.min(filteredOrders.value.length, pageStart.value + pageSize.value))
const pagedOrders = computed(() => filteredOrders.value.slice(pageStart.value, pageEnd.value))

const isPageAllSelected = computed(() => {
  if (pagedOrders.value.length === 0) return false
  return pagedOrders.value.every((order) => selectedOrders.value.includes(order.orderNo))
})

watch([filteredOrders, pageSize], () => {
  if (currentPage.value > totalPages.value) currentPage.value = totalPages.value
})

function canCancel(status) {
  return ['CREATED', 'LOCKED', 'PAID', 'CONFIRMED'].includes(status)
}

function resolveOrderItemName(item) {
  if (!item) return '未命名商品'
  return item.productTitle || item.productName || item.title || (item.productId ? `商品#${item.productId}` : '未命名商品')
}

function getItemNames(order) {
  if (!order.items || order.items.length === 0) return '-'
  return order.items.map((item) => `${resolveOrderItemName(item)}x${item.quantity}`).join('，')
}

function formatTime(value) {
  return value ? value.replace('T', ' ').substring(0, 16) : '-'
}

function parseTime(value) {
  if (!value) return NaN
  return Date.parse(value)
}

function applyClientFilters() {
  const kw = keyword.value.toLowerCase()
  const start = dateRange.value.start ? Date.parse(`${dateRange.value.start}T00:00:00`) : null
  const end = dateRange.value.end ? Date.parse(`${dateRange.value.end}T23:59:59`) : null

  filteredOrders.value = allOrders.value.filter((order) => {
    const t = parseTime(order.createdAt)
    if (start != null && (Number.isNaN(t) || t < start)) return false
    if (end != null && (Number.isNaN(t) || t > end)) return false

    if (!kw) return true
    const text = `${order.orderNo || ''} ${order.receiverName || ''} ${order.receiverPhone || ''}`.toLowerCase()
    return text.includes(kw)
  })
}

function applyFilters() {
  currentPage.value = 1
  selectedOrders.value = []
  applyClientFilters()
}

function resetFilters() {
  keyword.value = ''
  dateRange.value = { start: '', end: '' }
  currentPage.value = 1
  selectedOrders.value = []
  applyClientFilters()
}

function changeStatus(status) {
  currentStatus.value = status
  currentPage.value = 1
  selectedOrders.value = []
  loadOrders()
}

function goToPage(page) {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  selectedOrders.value = []
}

function handlePageSizeChange() {
  currentPage.value = 1
  selectedOrders.value = []
}

function toggleSelectPage() {
  const pageNos = pagedOrders.value.map((order) => order.orderNo)
  if (isPageAllSelected.value) {
    selectedOrders.value = selectedOrders.value.filter((no) => !pageNos.includes(no))
  } else {
    const merged = new Set([...selectedOrders.value, ...pageNos])
    selectedOrders.value = [...merged]
  }
}

function clearSelection() {
  selectedOrders.value = []
}

async function loadOrders() {
  loading.value = true
  try {
    const query = new URLSearchParams()
    query.append('limit', '500')
    if (currentStatus.value) query.append('status', currentStatus.value)
    const res = await api.get('/orders/all?' + query.toString())
    allOrders.value = res.success && Array.isArray(res.data) ? res.data : []
    applyClientFilters()
  } catch (e) {
    allOrders.value = []
    filteredOrders.value = []
    showToast('加载订单失败: ' + e.message)
  }
  loading.value = false
}

async function viewDetail(orderNo) {
  try {
    const res = await api.get('/orders/' + orderNo)
    if (res.success && res.data) {
      detail.value = res.data
      detailVisible.value = true
    } else {
      showToast(res.message || '加载详情失败')
    }
  } catch (e) {
    showToast('加载详情失败: ' + e.message)
  }
}

async function confirmOrder(orderNo) {
  if (!confirm('确认将该订单标记为已发货？')) return
  try {
    const res = await api.post('/orders/' + orderNo + '/confirm', {})
    if (res.success) {
      showToast('发货成功')
      loadOrders()
    } else {
      showToast(res.message || '操作失败')
    }
  } catch (e) {
    showToast('操作失败: ' + e.message)
  }
}

async function completeOrder(orderNo) {
  if (!confirm('确认完成该订单？')) return
  try {
    const res = await api.post('/orders/' + orderNo + '/complete', {})
    if (res.success) {
      showToast('订单已完成')
      loadOrders()
    } else {
      showToast(res.message || '操作失败')
    }
  } catch (e) {
    showToast('操作失败: ' + e.message)
  }
}

async function cancelOrder(orderNo) {
  const reason = prompt('请输入取消原因（可选）：')
  if (reason === null) return
  try {
    const body = reason ? { reason } : {}
    const res = await api.post('/orders/' + orderNo + '/cancel', body)
    if (res.success) {
      showToast('订单已取消')
      loadOrders()
    } else {
      showToast(res.message || '操作失败')
    }
  } catch (e) {
    showToast('操作失败: ' + e.message)
  }
}

async function batchComplete() {
  const targets = filteredOrders.value.filter((order) => selectedOrders.value.includes(order.orderNo) && order.status === 'CONFIRMED')
  if (targets.length === 0) {
    showToast('请先选择状态为“已发货”的订单')
    return
  }
  if (!confirm(`确认批量完成 ${targets.length} 条订单？`)) return

  try {
    const results = await Promise.all(targets.map((order) => api.post('/orders/' + order.orderNo + '/complete', {})))
    const successCount = results.filter((item) => item?.success).length
    showToast(`批量完成：${successCount}/${targets.length}`)
    selectedOrders.value = []
    loadOrders()
  } catch (e) {
    showToast('批量完成失败: ' + e.message)
  }
}

async function batchCancel() {
  const targets = filteredOrders.value.filter((order) => selectedOrders.value.includes(order.orderNo) && canCancel(order.status))
  if (targets.length === 0) {
    showToast('所选订单不可取消')
    return
  }
  const reason = prompt('请输入批量取消原因（可选）：')
  if (reason === null) return
  if (!confirm(`确认批量取消 ${targets.length} 条订单？`)) return

  try {
    const body = reason ? { reason } : {}
    const results = await Promise.all(targets.map((order) => api.post('/orders/' + order.orderNo + '/cancel', body)))
    const successCount = results.filter((item) => item?.success).length
    showToast(`批量取消：${successCount}/${targets.length}`)
    selectedOrders.value = []
    loadOrders()
  } catch (e) {
    showToast('批量取消失败: ' + e.message)
  }
}

async function releaseExpired() {
  try {
    const res = await api.post('/orders/release-expired', {})
    if (res.success) {
      const count = res.data?.releasedOrders ?? 0
      showToast('释放完成：' + count + ' 单')
      loadOrders()
    } else {
      showToast(res.message || '释放失败')
    }
  } catch (e) {
    showToast('释放失败: ' + e.message)
  }
}

function exportCsv() {
  if (filteredOrders.value.length === 0) {
    showToast('没有可导出的数据')
    return
  }

  const header = ['订单号', '收货人', '手机号', '商品', '金额', '状态', '创建时间']
  const rows = filteredOrders.value.map((order) => [
    order.orderNo || '',
    order.receiverName || '',
    order.receiverPhone || '',
    getItemNames(order),
    formatPrice(order.totalAmount),
    statusMap[order.status] || order.status || '',
    formatTime(order.createdAt)
  ])

  const csv = [header, ...rows]
    .map((row) => row.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(','))
    .join('\n')

  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `admin-orders-${new Date().toISOString().slice(0, 10)}.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

onMounted(loadOrders)
</script>

<style scoped>
.orders-actions {
  display: flex;
  gap: 8px;
}

.orders-filters {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.orders-filter-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}

.orders-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.orders-date-input {
  width: 140px;
}

.orders-keyword-input {
  min-width: 240px;
}

.orders-item-cell {
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.orders-batch-bar {
  margin-bottom: 16px;
}

.orders-batch-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

.orders-pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 14px;
  gap: 10px;
}

.orders-page-summary {
  font-size: 12px;
  color: var(--text-muted);
}

.orders-page-controls {
  display: flex;
  gap: 8px;
  align-items: center;
}

.orders-page-size {
  width: 100px;
  height: 30px;
}

.orders-detail-modal {
  width: 680px;
  max-height: 90vh;
}

.orders-detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.orders-detail-meta {
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.8;
}

.orders-detail-total {
  margin-top: 12px;
  text-align: right;
  font-weight: 700;
  color: var(--primary);
}

@media (max-width: 900px) {
  .orders-actions {
    flex-wrap: wrap;
  }

  .orders-keyword-input {
    min-width: 180px;
  }

  .orders-pagination {
    flex-direction: column;
    align-items: flex-start;
  }

  .orders-detail-modal {
    width: 100%;
  }
}
</style>
