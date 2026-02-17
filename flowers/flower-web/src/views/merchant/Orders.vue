<template>
  <div>
    <div class="top-bar">
      <h1 class="page-title">订单处理</h1>
      <button class="btn btn-outline" @click="releaseExpired">释放过期订单</button>
    </div>

    <!-- 状态筛选 -->
    <div class="card" style="margin-bottom:16px">
      <div class="card-body" style="display:flex;gap:8px;flex-wrap:wrap;padding:12px 16px">
        <button v-for="s in statusFilters" :key="s.value"
          class="btn btn-sm"
          :class="currentStatus === s.value ? 'btn-primary' : 'btn-outline'"
          @click="loadOrders(s.value)">{{ s.label }}</button>
      </div>
    </div>

    <!-- 订单列表 -->
    <div class="card">
      <div class="card-header"><span class="card-title">订单列表</span></div>
      <div class="card-body">
        <div class="table-wrapper">
          <table>
            <thead>
              <tr><th>订单号</th><th>商品</th><th>金额</th><th>收货人</th><th>状态</th><th>创建时间</th><th>操作</th></tr>
            </thead>
            <tbody>
              <tr v-if="loading"><td colspan="7" class="loading">加载中...</td></tr>
              <tr v-else-if="orders.length === 0"><td colspan="7" class="empty">暂无订单</td></tr>
              <tr v-for="o in orders" :key="o.orderNo" v-else>
                <td style="font-family:monospace;font-size:12px">{{ o.orderNo }}</td>
                <td style="max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ getItemNames(o) }}</td>
                <td style="font-weight:600;color:var(--primary)">&yen;{{ formatPrice(o.totalAmount) }}</td>
                <td>{{ getReceiver(o) }}</td>
                <td><span class="badge" :class="statusBadge[o.status] || 'badge-default'">{{ statusMap[o.status] || o.status }}</span></td>
                <td style="font-size:12px;color:var(--text-secondary)">{{ formatTime(o.createdAt) }}</td>
                <td style="white-space:nowrap">
                  <button class="btn btn-sm btn-outline" @click="viewDetail(o.orderNo)">详情</button>
                  <button v-if="o.status === 'PAID'" class="btn btn-sm btn-primary" @click="confirmOrder(o.orderNo)">确认发货</button>
                  <button v-if="o.status === 'PAID' || o.status === 'CONFIRMED'" class="btn btn-sm btn-success" @click="completeOrder(o.orderNo)">完成</button>
                  <button v-if="o.status === 'PAID' || o.status === 'CONFIRMED' || o.status === 'COMPLETED'" class="btn btn-sm btn-danger" @click="cancelOrder(o.orderNo)">取消/退款</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 订单详情 -->
    <div v-if="detailVisible" class="card" style="margin-top:16px">
      <div class="card-header">
        <span class="card-title">订单详情</span>
        <button class="btn btn-sm btn-outline" @click="detailVisible = false">关闭</button>
      </div>
      <div class="card-body">
        <div style="display:flex;justify-content:space-between;margin-bottom:12px">
          <span style="font-weight:600">{{ detail.orderNo }}</span>
          <span class="badge" :class="statusBadge[detail.status] || 'badge-default'">{{ statusMap[detail.status] || detail.status }}</span>
        </div>
        <div v-if="detail.receiverName" style="margin-bottom:12px">
          <strong>收货人：</strong>{{ detail.receiverName }} {{ detail.receiverPhone || '' }}<br>
          <strong>地址：</strong>{{ detail.receiverAddress || '-' }}
        </div>
        <table style="width:100%">
          <thead><tr><th>商品</th><th>单价</th><th>数量</th><th>小计</th></tr></thead>
          <tbody>
            <tr v-for="i in detail.items" :key="i.productTitle">
              <td>{{ i.productTitle }}</td>
              <td>&yen;{{ formatPrice(i.unitPrice) }}</td>
              <td>{{ i.quantity }}</td>
              <td>&yen;{{ formatPrice(i.lineAmount) }}</td>
            </tr>
          </tbody>
        </table>
        <div style="text-align:right;margin-top:12px;font-size:16px;font-weight:600;color:var(--primary)">合计：&yen;{{ formatPrice(detail.totalAmount) }}</div>
        <template v-if="detail.locks && detail.locks.length > 0">
          <div style="margin-top:16px"><strong>库存锁定：</strong></div>
          <table style="width:100%;margin-top:8px">
            <thead><tr><th>花材</th><th>锁定量</th><th>状态</th></tr></thead>
            <tbody>
              <tr v-for="l in detail.locks" :key="l.flowerName">
                <td>{{ l.flowerName }}</td>
                <td>{{ l.lockQty }}</td>
                <td><span class="badge badge-info">{{ l.lockStatus }}</span></td>
              </tr>
            </tbody>
          </table>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../../api'
import { formatPrice, showToast } from '../../utils'

const statusMap = { CREATED: '已创建', LOCKED: '待支付', PAID: '已支付', CONFIRMED: '已确认发货', COMPLETED: '已完成', CANCELLED: '已取消', REFUNDED: '已退款' }
const statusBadge = { PAID: 'badge-success', CONFIRMED: 'badge-info', COMPLETED: 'badge-success', CANCELLED: 'badge-default', REFUNDED: 'badge-warning', LOCKED: 'badge-warning', CREATED: 'badge-default' }
const statusFilters = [
  { value: '', label: '全部' },
  { value: 'PAID', label: '待确认' },
  { value: 'CONFIRMED', label: '已确认' },
  { value: 'LOCKED', label: '待支付' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'REFUNDED', label: '已退款' }
]

const orders = ref([])
const loading = ref(true)
const currentStatus = ref('')
const detailVisible = ref(false)
const detail = ref({})

function getItemNames(o) {
  return o.items ? o.items.map(i => i.productTitle + '×' + i.quantity).join(', ') : '-'
}
function getReceiver(o) {
  return o.receiverName ? (o.receiverName + (o.receiverPhone ? ' ' + o.receiverPhone : '')) : '-'
}
function formatTime(t) {
  return t ? t.replace('T', ' ').substring(0, 16) : '-'
}

async function loadOrders(status) {
  currentStatus.value = status
  loading.value = true
  try {
    const url = status ? '/orders/all?status=' + status + '&limit=50' : '/orders/all?limit=50'
    const res = await api.get(url)
    if (res.success && res.data) orders.value = res.data
    else orders.value = []
  } catch (e) { orders.value = [] }
  loading.value = false
}

async function viewDetail(orderNo) {
  try {
    const res = await api.get('/orders/' + orderNo)
    if (res.success && res.data) {
      detail.value = res.data
      detailVisible.value = true
    }
  } catch (e) { showToast('查询失败') }
}

async function confirmOrder(orderNo) {
  if (!confirm('确认发货该订单？')) return
  try {
    const res = await api.post('/orders/' + orderNo + '/confirm', {})
    if (res.success) { showToast('已确认发货'); loadOrders(currentStatus.value) }
    else { showToast(res.message || '操作失败') }
  } catch (e) { showToast('操作失败: ' + e.message) }
}

async function completeOrder(orderNo) {
  if (!confirm('确认完成该订单？')) return
  try {
    const res = await api.post('/orders/' + orderNo + '/complete', {})
    if (res.success) { showToast('订单已完成'); loadOrders(currentStatus.value) }
    else { showToast(res.message || '操作失败') }
  } catch (e) { showToast('操作失败: ' + e.message) }
}

async function cancelOrder(orderNo) {
  const reason = prompt('请输入取消/退款原因（可选）：')
  if (reason === null) return
  try {
    const res = await api.post('/orders/' + orderNo + '/cancel', { reason })
    if (res.success) { showToast('已取消/退款'); loadOrders(currentStatus.value) }
    else { showToast(res.message || '操作失败') }
  } catch (e) { showToast('操作失败: ' + e.message) }
}

async function releaseExpired() {
  try {
    const res = await api.post('/orders/release-expired', {})
    if (res.success) { showToast('释放完成'); loadOrders(currentStatus.value) }
  } catch (e) { showToast('操作失败') }
}

onMounted(() => loadOrders(''))
</script>
