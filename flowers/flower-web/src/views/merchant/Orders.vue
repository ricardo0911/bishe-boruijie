<template>
  <div>
    <div class="top-bar">
      <h1 class="page-title">订单处理</h1>
      <div class="orders-top-actions">
        <button class="btn btn-outline" @click="exportOrders">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
          导出订单
        </button>
        <button class="btn btn-outline" @click="releaseExpired">释放过期订单</button>
      </div>
    </div>

    <!-- 筛选区域 -->
    <div class="card">
      <div class="card-body orders-filter-body">
        <!-- 状态筛选 -->
        <div class="orders-status-row">
          <span class="orders-label orders-label-gap">订单状态：</span>
          <button v-for="s in statusFilters" :key="s.value"
            class="btn btn-sm"
            :class="currentStatus === s.value ? 'btn-primary' : 'btn-outline'"
            @click="handleStatusChange(s.value)">{{ s.label }}</button>
        </div>

        <!-- 搜索和时间筛选 -->
        <div class="orders-search-row">
          <div class="orders-date-range">
            <span class="orders-label">时间范围：</span>
            <input type="date" class="form-control orders-date-input" v-model="dateRange.start">
            <span class="orders-to-text">至</span>
            <input type="date" class="form-control orders-date-input" v-model="dateRange.end">
          </div>
          <div class="orders-search-group">
            <input type="text" class="form-control" v-model="searchKeyword" placeholder="搜索订单号/客户名" @keyup.enter="handleSearch">
            <button class="btn btn-primary" @click="handleSearch">搜索</button>
            <button class="btn btn-outline" @click="resetFilters">重置</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 批量操作栏 -->
    <div v-if="selectedOrders.length > 0" class="alert-bar warning orders-batch-bar">
      <span>已选择 {{ selectedOrders.length }} 个订单</span>
      <div class="orders-batch-actions">
        <button class="btn btn-sm btn-success" @click="batchShip">批量发货</button>
        <button class="btn btn-sm btn-danger" @click="batchCancel">批量取消</button>
        <button class="btn btn-sm btn-outline" @click="clearSelection">清空选择</button>
      </div>
    </div>

    <!-- 订单列表 -->
    <div class="card">
      <div class="card-header">
        <span class="card-title">订单列表</span>
        <span class="orders-total-tip">共 {{ total }} 条</span>
      </div>
      <div class="card-body orders-table-body">
        <div class="table-wrapper">
          <table>
            <thead>
              <tr>
                <th class="orders-th-select">
                  <input type="checkbox" :checked="isAllSelected" @change="toggleSelectAll">
                </th>
                <th>订单号</th>
                <th>客户</th>
                <th>商品</th>
                <th>金额</th>
                <th>状态</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading"><td colspan="8" class="loading">加载中...</td></tr>
              <tr v-else-if="orders.length === 0"><td colspan="8" class="empty">暂无订单</td></tr>
              <tr v-for="o in orders" :key="o.orderNo" v-else>
                <td class="orders-td-select">
                  <input type="checkbox" :value="o.orderNo" v-model="selectedOrders">
                </td>
                <td class="orders-order-no">{{ o.orderNo }}</td>
                <td>
                  <div>{{ o.receiverName || '-' }}</div>
                  <div class="orders-phone">{{ o.receiverPhone || '' }}</div>
                </td>
                <td class="orders-item-cell">{{ getItemNames(o) }}</td>
                <td class="orders-amount">&yen;{{ formatPrice(o.totalAmount) }}</td>
                <td><span class="badge" :class="statusBadge[o.status] || 'badge-default'">{{ statusMap[o.status] || o.status }}</span></td>
                <td class="orders-created">{{ formatTime(o.createdAt) }}</td>
                <td class="orders-actions-cell">
                  <button class="btn btn-sm btn-outline" @click="viewDetail(o.orderNo)">详情</button>
                  <button v-if="o.status === 'PAID'" class="btn btn-sm btn-primary" @click="openShipModal(o)">发货</button>
                  <button v-if="o.status === 'PAID' || o.status === 'CONFIRMED'" class="btn btn-sm btn-success" @click="completeOrder(o.orderNo)">完成</button>
                  <button v-if="canCancel(o.status)" class="btn btn-sm btn-danger" @click="cancelOrder(o.orderNo)">取消</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 分页 -->
        <div v-if="total > 0" class="orders-pagination">
          <div class="orders-page-summary">
            显示 {{ (currentPage - 1) * pageSize + 1 }} - {{ Math.min(currentPage * pageSize, total) }} 条
          </div>
          <div class="orders-page-actions">
            <button class="btn btn-sm btn-outline" :disabled="currentPage === 1" @click="goPage(currentPage - 1)">上一页</button>
            <button v-for="p in visiblePages" :key="p"
              class="btn btn-sm"
              :class="currentPage === p ? 'btn-primary' : 'btn-outline'"
              @click="goPage(p)">{{ p }}</button>
            <button class="btn btn-sm btn-outline" :disabled="currentPage === totalPages" @click="goPage(currentPage + 1)">下一页</button>
            <select v-model="pageSize" class="form-control orders-page-size" @change="handleSizeChange">
              <option :value="10">10</option>
              <option :value="20">20</option>
              <option :value="50">50</option>
            </select>
          </div>
        </div>
      </div>
    </div>

    <!-- 订单详情抽屉 -->
    <div v-if="detailVisible" class="modal">
      <div class="modal-overlay" @click="detailVisible = false"></div>
      <div class="modal-content orders-detail-modal">
        <div class="orders-detail-header">
          <h2 class="orders-detail-title">订单详情</h2>
          <button class="btn btn-sm btn-outline" @click="detailVisible = false">关闭</button>
        </div>

        <div class="orders-detail-meta">
          <div class="orders-detail-meta-row">
            <span class="orders-detail-no">{{ detail.orderNo }}</span>
            <span class="badge" :class="statusBadge[detail.status] || 'badge-default'">{{ statusMap[detail.status] || detail.status }}</span>
          </div>
        </div>

        <!-- 收货信息 -->
        <div class="orders-block orders-block-receiver">
          <div class="orders-block-title">收货信息</div>
          <div class="orders-grid-two orders-info-grid">
            <div><span class="orders-field-label">收货人：</span>{{ detail.receiverName || '-' }}</div>
            <div><span class="orders-field-label">电话：</span>{{ detail.receiverPhone || '-' }}</div>
            <div class="orders-span-two"><span class="orders-field-label">地址：</span>{{ detail.receiverAddress || '-' }}</div>
          </div>
        </div>

        <!-- 物流信息 -->
        <div v-if="detail.logisticsCompany" class="orders-block orders-block-logistics">
          <div class="orders-block-title">物流信息</div>
          <div class="orders-grid-two orders-info-grid">
            <div><span class="orders-field-label">物流公司：</span>{{ detail.logisticsCompany }}</div>
            <div><span class="orders-field-label">运单号：</span>{{ detail.trackingNo }}</div>
          </div>
        </div>

        <!-- 商品列表 -->
        <div class="orders-section">
          <div class="orders-block-title">商品明细</div>
          <table class="orders-detail-table">
            <thead>
              <tr class="orders-detail-head">
                <th class="orders-cell orders-cell-left">商品</th>
                <th class="orders-cell orders-cell-right">单价</th>
                <th class="orders-cell orders-cell-center">数量</th>
                <th class="orders-cell orders-cell-right">小计</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="i in detail.items" :key="(i.productId || 'p') + '-' + (i.productTitle || i.productName || i.title || 'item')" class="orders-row-border">
                <td class="orders-cell">{{ resolveOrderItemName(i) }}</td>
                <td class="orders-cell orders-cell-right">&yen;{{ formatPrice(i.unitPrice) }}</td>
                <td class="orders-cell orders-cell-center">{{ i.quantity }}</td>
                <td class="orders-cell orders-cell-right orders-cell-strong">&yen;{{ formatPrice(i.lineAmount) }}</td>
              </tr>
            </tbody>
          </table>
          <div class="orders-total">合计：&yen;{{ formatPrice(detail.totalAmount) }}</div>
        </div>

        <!-- 库存锁定 -->
        <template v-if="detail.locks && detail.locks.length > 0">
          <div class="orders-block-title">库存锁定</div>
          <table class="orders-detail-table orders-lock-table">
            <thead>
              <tr class="orders-detail-head">
                <th class="orders-cell orders-cell-left">花材</th>
                <th class="orders-cell orders-cell-center">锁定量</th>
                <th class="orders-cell orders-cell-center">状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="l in detail.locks" :key="l.flowerName" class="orders-row-border">
                <td class="orders-cell">{{ l.flowerName }}</td>
                <td class="orders-cell orders-cell-center">{{ l.lockQty }}</td>
                <td class="orders-cell orders-cell-center"><span class="badge badge-info">{{ l.lockStatus }}</span></td>
              </tr>
            </tbody>
          </table>
        </template>

        <!-- 操作记录 -->
        <div v-if="detail.remarks" class="orders-remark">
          <strong>备注：</strong>{{ detail.remarks }}
        </div>
      </div>
    </div>

    <!-- 发货弹窗 -->
    <div v-if="shipModalVisible" class="modal">
      <div class="modal-overlay" @click="closeShipModal"></div>
      <div class="modal-content orders-ship-modal">
        <h2>订单发货</h2>
        <div class="orders-ship-info">
          <div><span class="orders-field-label">订单号：</span>{{ currentShipOrder.orderNo }}</div>
          <div><span class="orders-field-label">收货人：</span>{{ currentShipOrder.receiverName }} {{ currentShipOrder.receiverPhone }}</div>
        </div>
        <form @submit.prevent="confirmShip">
          <div class="form-group">
            <label>物流公司 <span class="orders-required">*</span></label>
            <select class="form-control" v-model="shipForm.logisticsCompany" required>
              <option value="">请选择</option>
              <option value="顺丰速运">顺丰速运</option>
              <option value="中通快递">中通快递</option>
              <option value="圆通速递">圆通速递</option>
              <option value="申通快递">申通快递</option>
              <option value="韵达快递">韵达快递</option>
              <option value="EMS">EMS</option>
              <option value="京东物流">京东物流</option>
              <option value="德邦快递">德邦快递</option>
              <option value="其他">其他</option>
            </select>
          </div>
          <div class="form-group">
            <label>运单号 <span class="orders-required">*</span></label>
            <input type="text" class="form-control" v-model="shipForm.trackingNo" placeholder="请输入运单号" required>
          </div>
          <div class="form-group" v-if="shipForm.logisticsCompany === '其他'">
            <label>物流公司名称</label>
            <input type="text" class="form-control" v-model="shipForm.customCompany" placeholder="请输入物流公司名称">
          </div>
          <div class="form-group">
            <label>备注</label>
            <textarea class="form-control" v-model="shipForm.remarks" rows="2" placeholder="可选"></textarea>
          </div>
          <div class="form-actions">
            <button type="button" class="btn btn-outline" @click="closeShipModal">取消</button>
            <button type="submit" class="btn btn-primary">确认发货</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 批量发货弹窗 -->
    <div v-if="batchShipModalVisible" class="modal">
      <div class="modal-overlay" @click="closeBatchShipModal"></div>
      <div class="modal-content orders-ship-modal">
        <h2>批量发货</h2>
        <div class="orders-ship-info">
          <div>已选择 <strong>{{ selectedOrders.length }}</strong> 个订单</div>
          <div class="orders-ship-order-list">订单号：{{ selectedOrders.slice(0, 3).join(', ') }}{{ selectedOrders.length > 3 ? '...' : '' }}</div>
        </div>
        <form @submit.prevent="confirmBatchShip">
          <div class="form-group">
            <label>物流公司 <span class="orders-required">*</span></label>
            <select class="form-control" v-model="batchShipForm.logisticsCompany" required>
              <option value="">请选择</option>
              <option value="顺丰速运">顺丰速运</option>
              <option value="中通快递">中通快递</option>
              <option value="圆通速递">圆通速递</option>
              <option value="申通快递">申通快递</option>
              <option value="韵达快递">韵达快递</option>
              <option value="EMS">EMS</option>
              <option value="京东物流">京东物流</option>
              <option value="德邦快递">德邦快递</option>
              <option value="其他">其他</option>
            </select>
          </div>
          <div class="form-group" v-if="batchShipForm.logisticsCompany === '其他'">
            <label>物流公司名称</label>
            <input type="text" class="form-control" v-model="batchShipForm.customCompany" placeholder="请输入物流公司名称">
          </div>
          <div class="form-group">
            <label>备注</label>
            <textarea class="form-control" v-model="batchShipForm.remarks" rows="2" placeholder="可选，将应用到所有订单"></textarea>
          </div>
          <div class="orders-tip-warning">
            提示：批量发货将为每个订单生成不同的运单号，系统会自动分配序列号。
          </div>
          <div class="form-actions">
            <button type="button" class="btn btn-outline" @click="closeBatchShipModal">取消</button>
            <button type="submit" class="btn btn-primary">确认批量发货</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { api } from '../../api'
import { formatPrice, showToast } from '../../utils'

// 状态映射
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
  { value: 'PAID', label: '待发货' },
  { value: 'CONFIRMED', label: '已发货' },
  { value: 'LOCKED', label: '待支付' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'REFUNDED', label: '已退款' }
]

// 数据
const orders = ref([])
const allOrders = ref([])
const loading = ref(true)
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const currentStatus = ref('')
const searchKeyword = ref('')
const dateRange = ref({ start: '', end: '' })
const selectedOrders = ref([])
const detailVisible = ref(false)
const detail = ref({})

// 发货弹窗
const shipModalVisible = ref(false)
const currentShipOrder = ref({})
const shipForm = ref({
  logisticsCompany: '',
  trackingNo: '',
  customCompany: '',
  remarks: ''
})

// 批量发货弹窗
const batchShipModalVisible = ref(false)
const batchShipForm = ref({
  logisticsCompany: '',
  customCompany: '',
  remarks: ''
})

// 计算属性
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

const visiblePages = computed(() => {
  const pages = []
  const maxVisible = 5
  let start = Math.max(1, currentPage.value - Math.floor(maxVisible / 2))
  let end = Math.min(totalPages.value, start + maxVisible - 1)
  if (end - start + 1 < maxVisible) {
    start = Math.max(1, end - maxVisible + 1)
  }
  for (let i = start; i <= end; i++) {
    pages.push(i)
  }
  return pages
})

const isAllSelected = computed(() => {
  return orders.value.length > 0 && selectedOrders.value.length === orders.value.length
})

// 方法
function resolveOrderItemName(item) {
  if (!item) return '未命名商品'
  return item.productTitle || item.productName || item.title || (item.productId ? `商品#${item.productId}` : '未命名商品')
}

function getItemNames(o) {
  return o.items ? o.items.map(i => resolveOrderItemName(i) + '×' + i.quantity).join(', ') : '-'
}

function formatTime(t) {
  return t ? t.replace('T', ' ').substring(0, 16) : '-'
}

function canCancel(status) {
  return ['PAID', 'CONFIRMED', 'LOCKED', 'CREATED'].includes(status)
}

function buildQueryParams() {
  const params = new URLSearchParams()
  params.append('limit', '200')
  if (currentStatus.value) {
    params.append('status', currentStatus.value)
  }
  return params.toString()
}

function applyClientFilters(orderList) {
  const keyword = searchKeyword.value.trim().toLowerCase()
  const start = dateRange.value.start ? new Date(`${dateRange.value.start}T00:00:00`).getTime() : null
  const end = dateRange.value.end ? new Date(`${dateRange.value.end}T23:59:59`).getTime() : null

  return orderList.filter((order) => {
    const createdTime = order.createdAt ? Date.parse(order.createdAt) : NaN
    const inDateRange =
      (start == null || (!Number.isNaN(createdTime) && createdTime >= start)) &&
      (end == null || (!Number.isNaN(createdTime) && createdTime <= end))
    if (!inDateRange) return false

    if (!keyword) return true
    const haystack = `${order.orderNo || ''} ${order.receiverName || ''}`.toLowerCase()
    return haystack.includes(keyword)
  })
}

function paginateOrders(orderList) {
  const startIndex = (currentPage.value - 1) * pageSize.value
  return orderList.slice(startIndex, startIndex + pageSize.value)
}

async function loadOrders() {
  loading.value = true
  try {
    const query = buildQueryParams()
    const res = await api.get('/orders/all?' + query)
    const serverOrders = res.success && Array.isArray(res.data) ? res.data : []
    const filtered = applyClientFilters(serverOrders)
    allOrders.value = filtered
    total.value = filtered.length

    const maxPage = Math.max(1, Math.ceil(total.value / pageSize.value))
    if (currentPage.value > maxPage) {
      currentPage.value = maxPage
    }
    orders.value = paginateOrders(filtered)
  } catch (e) {
    allOrders.value = []
    orders.value = []
    total.value = 0
    showToast('加载失败: ' + e.message)
  }
  loading.value = false
}

function handleStatusChange(status) {
  currentStatus.value = status
  currentPage.value = 1
  selectedOrders.value = []
  loadOrders()
}

function handleSearch() {
  currentPage.value = 1
  selectedOrders.value = []
  loadOrders()
}

function resetFilters() {
  currentStatus.value = ''
  searchKeyword.value = ''
  dateRange.value = { start: '', end: '' }
  currentPage.value = 1
  selectedOrders.value = []
  loadOrders()
}

function goPage(page) {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  selectedOrders.value = []
  loadOrders()
}

function handleSizeChange() {
  currentPage.value = 1
  selectedOrders.value = []
  loadOrders()
}

function toggleSelectAll() {
  if (isAllSelected.value) {
    selectedOrders.value = []
  } else {
    selectedOrders.value = orders.value.map(o => o.orderNo)
  }
}

function clearSelection() {
  selectedOrders.value = []
}

async function viewDetail(orderNo) {
  try {
    const res = await api.get('/orders/' + orderNo)
    if (res.success && res.data) {
      detail.value = res.data
      detailVisible.value = true
    } else {
      showToast('查询失败')
    }
  } catch (e) {
    showToast('查询失败: ' + e.message)
  }
}

// 发货相关
function openShipModal(order) {
  currentShipOrder.value = order
  shipForm.value = {
    logisticsCompany: '',
    trackingNo: '',
    customCompany: '',
    remarks: ''
  }
  shipModalVisible.value = true
}

function closeShipModal() {
  shipModalVisible.value = false
  currentShipOrder.value = {}
}

async function confirmShip() {
  const company = shipForm.value.logisticsCompany === '其他'
    ? shipForm.value.customCompany
    : shipForm.value.logisticsCompany

  if (!company) {
    showToast('请输入物流公司名称')
    return
  }
  if (!shipForm.value.trackingNo.trim()) {
    showToast('请输入运单号')
    return
  }

  try {
    const res = await api.post('/orders/' + currentShipOrder.value.orderNo + '/confirm', {
      logisticsCompany: company,
      trackingNo: shipForm.value.trackingNo.trim(),
      remarks: shipForm.value.remarks
    })
    if (res.success) {
      showToast('发货成功')
      closeShipModal()
      loadOrders()
    } else {
      showToast(res.message || '发货失败')
    }
  } catch (e) {
    showToast('发货失败: ' + e.message)
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
    const res = await api.post('/orders/' + orderNo + '/cancel', {
      reason: reason || undefined
    })
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

// 批量操作
function batchShip() {
  if (selectedOrders.value.length === 0) {
    showToast('请先选择订单')
    return
  }
  batchShipForm.value = {
    logisticsCompany: '',
    customCompany: '',
    remarks: ''
  }
  batchShipModalVisible.value = true
}

function closeBatchShipModal() {
  batchShipModalVisible.value = false
}

async function confirmBatchShip() {
  const company = batchShipForm.value.logisticsCompany === '其他'
    ? batchShipForm.value.customCompany
    : batchShipForm.value.logisticsCompany

  if (!company) {
    showToast('请输入物流公司名称')
    return
  }

  const targetOrders = orders.value.filter(
    (o) => selectedOrders.value.includes(o.orderNo) && o.status === 'PAID'
  )
  if (targetOrders.length === 0) {
    showToast('请选择待发货订单')
    return
  }

  try {
    const results = await Promise.all(
      targetOrders.map((order) =>
        api.post('/orders/' + order.orderNo + '/confirm', {
          logisticsCompany: company,
          trackingNo: `${Date.now()}_${order.orderNo.slice(-6)}`,
          remarks: batchShipForm.value.remarks
        })
      )
    )
    const successCount = results.filter((item) => item && item.success).length
    showToast(`批量发货完成：成功 ${successCount} / ${targetOrders.length}`)
    closeBatchShipModal()
    selectedOrders.value = []
    loadOrders()
  } catch (e) {
    showToast('批量发货失败: ' + e.message)
  }
}

async function batchCancel() {
  if (selectedOrders.value.length === 0) {
    showToast('请先选择订单')
    return
  }
  const reason = prompt('请输入批量取消原因（可选）：')
  if (reason === null) return

  if (!confirm(`确定要取消选中的 ${selectedOrders.value.length} 个订单吗？`)) return

  try {
    const results = await Promise.all(
      selectedOrders.value.map((orderNo) =>
        api.post('/orders/' + orderNo + '/cancel', {
          reason: reason || undefined
        })
      )
    )
    const successCount = results.filter((item) => item && item.success).length
    showToast(`批量取消完成：成功 ${successCount} / ${selectedOrders.value.length}`)
    selectedOrders.value = []
    loadOrders()
  } catch (e) {
    showToast('批量取消失败: ' + e.message)
  }
}

// 导出订单
async function exportOrders() {
  exportAsCsv()
}

function exportAsCsv() {
  const headers = ['订单号', '客户', '电话', '商品', '金额', '状态', '创建时间']
  const rows = allOrders.value.map(o => [
    o.orderNo,
    o.receiverName,
    o.receiverPhone,
    getItemNames(o),
    o.totalAmount,
    statusMap[o.status] || o.status,
    formatTime(o.createdAt)
  ])
  const csv = [headers, ...rows].map(r => r.map(field => `"${String(field).replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `订单导出_${new Date().toISOString().slice(0, 10)}.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
  showToast('CSV导出成功')
}

async function releaseExpired() {
  try {
    const res = await api.post('/orders/release-expired', {})
    if (res.success) {
      showToast('释放完成')
      loadOrders()
    }
  } catch (e) {
    showToast('操作失败')
  }
}

onMounted(() => loadOrders())
</script>

<style scoped>
.orders-top-actions {
  display: flex;
  gap: 10px;
}

.orders-filter-body {
  padding: 16px 20px;
}

.orders-status-row,
.orders-search-row,
.orders-date-range,
.orders-search-group,
.orders-page-actions,
.orders-detail-header,
.orders-detail-meta-row,
.orders-batch-actions {
  display: flex;
  align-items: center;
}

.orders-status-row {
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.orders-search-row {
  gap: 12px;
  flex-wrap: wrap;
}

.orders-date-range {
  gap: 8px;
}

.orders-search-group {
  gap: 8px;
  flex: 1;
  min-width: 200px;
}

.orders-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.orders-label-gap {
  margin-right: 4px;
}

.orders-date-input {
  width: 140px;
}

.orders-to-text,
.orders-total-tip,
.orders-page-summary,
.orders-phone,
.orders-created,
.orders-remark,
.orders-ship-order-list,
.orders-field-label {
  color: var(--text-muted);
}

.orders-batch-bar {
  margin-bottom: 16px;
}

.orders-batch-actions {
  margin-left: auto;
  gap: 8px;
}

.orders-table-body {
  padding: 0;
}

.orders-th-select,
.orders-td-select,
.orders-cell-center {
  text-align: center;
}

.orders-th-select {
  width: 40px;
}

.orders-order-no,
.orders-detail-no {
  font-family: monospace;
}

.orders-order-no {
  font-size: 12px;
}

.orders-item-cell {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.orders-amount,
.orders-total {
  color: var(--primary);
  font-weight: 600;
}

.orders-created,
.orders-phone,
.orders-page-summary,
.orders-total-tip,
.orders-info-grid,
.orders-remark,
.orders-ship-order-list,
.orders-ship-info {
  font-size: 13px;
}

.orders-actions-cell {
  white-space: nowrap;
}

.orders-pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid var(--line);
}

.orders-page-actions {
  gap: 6px;
}

.orders-page-size {
  width: 70px;
  height: 28px;
}

.orders-detail-modal {
  width: 640px;
  max-height: 90vh;
}

.orders-detail-header {
  justify-content: space-between;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
}

.orders-detail-title {
  font-size: 18px;
  font-weight: 700;
}

.orders-detail-meta,
.orders-section,
.orders-lock-table,
.orders-ship-info {
  margin-bottom: 16px;
}

.orders-detail-meta-row {
  justify-content: space-between;
  margin-bottom: 12px;
}

.orders-detail-no {
  font-size: 14px;
}

.orders-block {
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}

.orders-block-receiver {
  background: #fff8fb;
}

.orders-block-logistics {
  background: #f1f4ff;
}

.orders-block-title {
  margin-bottom: 12px;
  font-weight: 600;
}

.orders-grid-two {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.orders-span-two {
  grid-column: span 2;
}

.orders-detail-table {
  width: 100%;
  font-size: 13px;
}

.orders-detail-head {
  background: #fff6f9;
}

.orders-cell {
  padding: 10px;
}

.orders-cell-left {
  text-align: left;
}

.orders-cell-right {
  text-align: right;
}

.orders-cell-strong {
  font-weight: 500;
}

.orders-row-border {
  border-bottom: 1px solid var(--line);
}

.orders-total {
  margin-top: 12px;
  text-align: right;
  font-size: 16px;
}

.orders-remark {
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

.orders-ship-modal {
  width: 480px;
}

.orders-ship-info {
  padding: 12px;
  border-radius: 8px;
  background: #fff8fb;
}

.orders-ship-order-list {
  margin-top: 4px;
}

.orders-required {
  color: var(--danger);
}

.orders-tip-warning {
  margin-bottom: 16px;
  padding: 10px 12px;
  border-radius: 6px;
  font-size: 12px;
  color: #d48806;
  background: #fff8e6;
}

@media (max-width: 900px) {
  .orders-top-actions,
  .orders-search-group,
  .orders-page-actions {
    flex-wrap: wrap;
  }

  .orders-date-range {
    width: 100%;
    flex-wrap: wrap;
  }

  .orders-date-input {
    width: calc(50% - 16px);
  }

  .orders-pagination {
    flex-direction: column;
    align-items: flex-start;
  }

  .orders-detail-modal,
  .orders-ship-modal {
    width: 100%;
  }

  .orders-grid-two {
    grid-template-columns: 1fr;
  }

  .orders-span-two {
    grid-column: auto;
  }
}
</style>
