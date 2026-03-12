<template>
  <div>
    <div class="top-bar">
      <h1 class="page-title">售后管理</h1>
      <div class="after-sale-top-actions">
        <button class="btn btn-outline" :disabled="loading" @click="loadRecords">刷新列表</button>
      </div>
    </div>

    <div v-if="errorMessage" class="alert-bar danger">{{ errorMessage }}</div>

    <div class="card">
      <div class="card-body after-sale-filter-body">
        <div class="after-sale-filter-row">
          <span class="after-sale-filter-label">售后状态：</span>
          <button
            v-for="option in statusFilters"
            :key="option.value"
            class="btn btn-sm"
            :class="currentStatus === option.value ? 'btn-primary' : 'btn-outline'"
            @click="handleStatusChange(option.value)"
          >
            {{ option.label }}
          </button>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-header">
        <span class="card-title">售后申请列表</span>
        <span class="after-sale-total-tip">共 {{ records.length }} 条</span>
      </div>
      <div class="card-body after-sale-table-body">
        <div class="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>退款单号</th>
                <th>订单号</th>
                <th>金额</th>
                <th>原因</th>
                <th>状态</th>
                <th>申请时间</th>
                <th>处理信息</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading"><td colspan="8" class="loading">加载中...</td></tr>
              <tr v-else-if="records.length === 0"><td colspan="8" class="empty">暂无售后申请</td></tr>
              <tr v-for="item in records" :key="item.id" v-else>
                <td class="after-sale-code">{{ item.refundNo }}</td>
                <td>{{ item.orderNo }}</td>
                <td class="after-sale-amount">&yen;{{ formatAmount(item.refundAmount) }}</td>
                <td>
                  <div>{{ formatReason(item) }}</div>
                  <div v-if="formatDescription(item)" class="after-sale-subline">{{ formatDescription(item) }}</div>
                </td>
                <td>
                  <span class="badge" :class="badgeMap[item.status] || 'badge-default'">
                    {{ statusMap[item.status] || item.status }}
                  </span>
                </td>
                <td>{{ formatDateTime(item.applyTime || item.createdAt) }}</td>
                <td>
                  <div v-if="formatRejectReason(item)" class="after-sale-subline">拒绝：{{ formatRejectReason(item) }}</div>
                  <div v-if="item.transactionId" class="after-sale-subline">退款流水：{{ item.transactionId }}</div>
                  <div v-if="item.refundTime" class="after-sale-subline">完成：{{ formatDateTime(item.refundTime) }}</div>
                </td>
                <td>
                  <div class="after-sale-actions">
                    <button
                      v-if="item.status === 'REFUND_REQUESTED'"
                      class="btn btn-sm btn-success"
                      :disabled="actionId === item.id"
                      @click="approve(item)"
                    >审核通过</button>
                    <button
                      v-if="item.status === 'REFUND_REQUESTED'"
                      class="btn btn-sm btn-danger"
                      :disabled="actionId === item.id"
                      @click="reject(item)"
                    >拒绝</button>
                    <button
                      v-if="item.status === 'REFUNDING'"
                      class="btn btn-sm btn-primary"
                      :disabled="actionId === item.id"
                      @click="process(item)"
                    >执行退款</button>
                    <span v-if="actionId === item.id" class="after-sale-pending">处理中...</span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../../api'

const loading = ref(false)
const actionId = ref(null)
const currentStatus = ref('')
const errorMessage = ref('')
const records = ref([])

const statusFilters = [
  { value: '', label: '全部' },
  { value: 'REFUND_REQUESTED', label: '待审核' },
  { value: 'REFUNDING', label: '退款中' },
  { value: 'REFUNDED', label: '已退款' },
  { value: 'REJECTED', label: '已拒绝' },
  { value: 'REFUND_FAILED', label: '退款失败' }
]

const statusMap = {
  REFUND_REQUESTED: '待审核',
  REFUNDING: '退款中',
  REFUNDED: '已退款',
  REJECTED: '已拒绝',
  REFUND_FAILED: '退款失败'
}

const badgeMap = {
  REFUND_REQUESTED: 'badge-warning',
  REFUNDING: 'badge-info',
  REFUNDED: 'badge-success',
  REJECTED: 'badge-danger',
  REFUND_FAILED: 'badge-danger'
}

function formatAmount(value) {
  const amount = Number(value || 0)
  return Number.isFinite(amount) ? amount.toFixed(2) : '0.00'
}

function formatDateTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ')
}

function repairUtf8Mojibake(value) {
  const text = String(value || '').trim()
  if (!text) return ''
  try {
    const bytes = Uint8Array.from([...text].map(char => char.charCodeAt(0) & 0xff))
    const decoded = new TextDecoder('utf-8').decode(bytes)
    if (/[\u4e00-\u9fa5]/.test(decoded)) {
      return decoded
    }
  } catch {
  }
  return text
}

function looksBrokenText(value) {
  if (!value) return false
  const text = String(value).trim()
  if (!text) return false
  return text.includes('�') || /[À-ÿ]/.test(text) || /\?{3,}/.test(text) || (!/[\u4e00-\u9fa5]/.test(text) && /\?{2,}/.test(text))
}

function normalizeText(value, fallback = '-') {
  const repaired = repairUtf8Mojibake(value)
  if (!repaired) return fallback
  if (looksBrokenText(repaired)) return fallback
  return repaired
}

function formatReason(item) {
  return normalizeText(item.reason, '售后原因待核实')
}

function formatDescription(item) {
  const description = normalizeText(item.description, '')
  const reason = normalizeText(item.reason, '')
  if (!description || description === reason) return ''
  return description
}

function formatRejectReason(item) {
  return normalizeText(item.rejectReason, '')
}

async function loadRecords() {
  loading.value = true
  errorMessage.value = ''
  try {
    const params = new URLSearchParams({ limit: '100' })
    if (currentStatus.value) params.set('status', currentStatus.value)
    const res = await api.get(`/after-sales/list?${params.toString()}`)
    if (!res.success) {
      errorMessage.value = res.message || '加载售后申请失败'
      records.value = []
      return
    }
    records.value = Array.isArray(res.data) ? res.data : []
  } catch {
    errorMessage.value = '网络异常，请稍后重试'
    records.value = []
  } finally {
    loading.value = false
  }
}

function handleStatusChange(status) {
  currentStatus.value = status
  loadRecords()
}

async function approve(item) {
  actionId.value = item.id
  try {
    const res = await api.post(`/after-sales/${item.id}/approve`, {})
    if (!res.success) {
      window.alert(res.message || '审核通过失败')
      return
    }
    await loadRecords()
  } finally {
    actionId.value = null
  }
}

async function reject(item) {
  const rejectReason = window.prompt('请输入拒绝原因（可选）', item.rejectReason || '')
  if (rejectReason === null) return

  actionId.value = item.id
  try {
    const res = await api.post(`/after-sales/${item.id}/reject`, { rejectReason })
    if (!res.success) {
      window.alert(res.message || '拒绝失败')
      return
    }
    await loadRecords()
  } finally {
    actionId.value = null
  }
}

async function process(item) {
  const refundId = window.prompt('请输入退款流水号', item.transactionId || `WXRF${Date.now()}`)
  if (!refundId) return

  actionId.value = item.id
  try {
    const res = await api.post(`/after-sales/${item.id}/process`, { refundId })
    if (!res.success) {
      window.alert(res.message || '执行退款失败')
      return
    }
    await loadRecords()
  } finally {
    actionId.value = null
  }
}

onMounted(() => {
  loadRecords()
})
</script>

<style scoped>
.after-sale-top-actions,
.after-sale-actions,
.after-sale-filter-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.after-sale-filter-body,
.after-sale-table-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.after-sale-filter-label,
.after-sale-total-tip,
.after-sale-subline,
.after-sale-pending {
  color: var(--text-secondary);
}

.after-sale-code,
.after-sale-amount {
  font-weight: 600;
}

.after-sale-subline {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
}

.alert-bar.danger {
  margin-bottom: 16px;
}
</style>