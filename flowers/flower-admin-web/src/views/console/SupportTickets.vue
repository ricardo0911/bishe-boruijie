<template>
  <div>
    <div class="top-bar">
      <h1 class="page-title">反馈管理</h1>
      <div class="ticket-actions">
        <button class="btn btn-outline" @click="loadTickets">刷新</button>
      </div>
    </div>

    <div class="card">
      <div class="card-body ticket-filters">
        <div class="ticket-filter-row">
          <span class="ticket-label">状态：</span>
          <button
            v-for="item in statusFilters"
            :key="item.value"
            class="btn btn-sm"
            :class="currentStatus === item.value ? 'btn-primary' : 'btn-outline'"
            @click="changeStatus(item.value)"
          >
            {{ item.label }}
          </button>
        </div>

        <div class="ticket-filter-row">
          <input
            v-model.trim="keyword"
            class="form-control ticket-keyword-input"
            placeholder="搜索工单号 / 标题 / 联系人 / 手机号"
            @keyup.enter="loadTickets"
          >
          <button class="btn btn-primary" @click="loadTickets">搜索</button>
          <button class="btn btn-outline" @click="resetFilters">重置</button>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-header">
        <span class="card-title">反馈列表</span>
        <span style="font-size:12px;color:var(--text-muted)">共 {{ tickets.length }} 条</span>
      </div>
      <div class="card-body">
        <div class="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>工单号</th>
                <th>问题类型</th>
                <th>问题标题</th>
                <th>联系人</th>
                <th>关联订单</th>
                <th>状态</th>
                <th>提交时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading"><td colspan="8" class="loading">加载中...</td></tr>
              <tr v-else-if="tickets.length === 0"><td colspan="8" class="empty">暂无工单</td></tr>
              <tr v-for="ticket in tickets" :key="ticket.ticketNo" v-else>
                <td class="ticket-mono">{{ ticket.ticketNo }}</td>
                <td>{{ issueTypeMap[ticket.issueType] || ticket.issueType }}</td>
                <td>
                  <div class="ticket-title">{{ ticket.title }}</div>
                  <div class="ticket-sub">{{ sliceText(ticket.content) }}</div>
                </td>
                <td>
                  <div>{{ ticket.contactName || '-' }}</div>
                  <div class="ticket-sub">{{ ticket.contactPhone || '-' }}</div>
                </td>
                <td>{{ ticket.orderNo || '-' }}</td>
                <td>
                  <span class="badge" :class="statusBadge[ticket.status] || 'badge-default'">
                    {{ statusMap[ticket.status] || ticket.status }}
                  </span>
                </td>
                <td class="ticket-sub">{{ formatTime(ticket.createdAt) }}</td>
                <td>
                  <button class="btn btn-sm btn-outline" @click="viewDetail(ticket.id)">详情</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div v-if="detailVisible" class="modal">
      <div class="modal-overlay" @click="detailVisible = false"></div>
      <div class="modal-content ticket-modal">
        <div class="ticket-modal-header">
          <h2>反馈详情</h2>
          <button class="btn btn-sm btn-outline" @click="detailVisible = false">关闭</button>
        </div>

        <div class="ticket-detail-grid">
          <div><strong>工单号：</strong><span class="ticket-mono">{{ detail.ticketNo }}</span></div>
          <div><strong>状态：</strong>{{ statusMap[detail.status] || detail.status }}</div>
          <div><strong>问题类型：</strong>{{ issueTypeMap[detail.issueType] || detail.issueType }}</div>
          <div><strong>用户ID：</strong>{{ detail.userId || '-' }}</div>
          <div><strong>联系人：</strong>{{ detail.contactName || '-' }}</div>
          <div><strong>联系电话：</strong>{{ detail.contactPhone || '-' }}</div>
          <div><strong>关联订单：</strong>{{ detail.orderNo || '-' }}</div>
          <div><strong>提交时间：</strong>{{ formatTime(detail.createdAt) }}</div>
        </div>

        <div class="ticket-block">
          <div class="ticket-block-title">问题标题</div>
          <div class="ticket-block-content">{{ detail.title || '-' }}</div>
        </div>

        <div class="ticket-block">
          <div class="ticket-block-title">问题描述</div>
          <div class="ticket-block-content ticket-content">{{ detail.content || '-' }}</div>
        </div>

        <div class="ticket-block">
          <div class="ticket-block-title">处理备注</div>
          <textarea v-model.trim="handleNote" class="form-control ticket-textarea" placeholder="记录处理结果、回访情况或补偿说明"></textarea>
        </div>

        <div class="ticket-process-row">
          <select v-model="processingStatus" class="form-control ticket-status-select">
            <option value="PROCESSING">处理中</option>
            <option value="RESOLVED">已解决</option>
            <option value="CLOSED">已关闭</option>
          </select>
          <button class="btn btn-primary" :disabled="processing" @click="submitProcess">
            {{ processing ? '提交中...' : '更新处理状态' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../../api'
import { showToast } from '../../utils'

const loading = ref(false)
const processing = ref(false)
const tickets = ref([])
const keyword = ref('')
const currentStatus = ref('')
const detailVisible = ref(false)
const detail = ref({})
const processingStatus = ref('PROCESSING')
const handleNote = ref('')

const statusMap = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  RESOLVED: '已解决',
  CLOSED: '已关闭'
}

const statusBadge = {
  PENDING: 'badge-warning',
  PROCESSING: 'badge-info',
  RESOLVED: 'badge-success',
  CLOSED: 'badge-default'
}

const issueTypeMap = {
  ORDER: '订单问题',
  DELIVERY: '配送问题',
  REFUND: '退款售后',
  PRODUCT: '商品问题',
  ACCOUNT: '账户问题',
  OTHER: '其他问题'
}

const statusFilters = [
  { value: '', label: '全部' },
  { value: 'PENDING', label: '待处理' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'RESOLVED', label: '已解决' },
  { value: 'CLOSED', label: '已关闭' }
]

function buildListUrl() {
  const params = new URLSearchParams()
  params.set('limit', '100')
  if (currentStatus.value) params.set('status', currentStatus.value)
  if (keyword.value) params.set('keyword', keyword.value)
  return `/support-tickets?${params.toString()}`
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').substring(0, 16) : '-'
}

function sliceText(text) {
  const value = String(text || '')
  return value.length > 28 ? `${value.slice(0, 28)}...` : value || '-'
}

function changeStatus(status) {
  currentStatus.value = status
  loadTickets()
}

function resetFilters() {
  keyword.value = ''
  currentStatus.value = ''
  loadTickets()
}

async function loadTickets() {
  loading.value = true
  try {
    const res = await api.listSupportTickets({ status: currentStatus.value, keyword: keyword.value, limit: 100 })
    if (!res.success) {
      showToast(res.message || '工单加载失败')
      tickets.value = []
      return
    }
    tickets.value = Array.isArray(res.data) ? res.data : []
  } catch (error) {
    tickets.value = []
    showToast('工单加载失败')
  } finally {
    loading.value = false
  }
}

async function viewDetail(id) {
  const res = await api.getSupportTicket(id)
  if (!res.success || !res.data) {
    showToast(res.message || '工单详情加载失败')
    return
  }
  detail.value = res.data
  handleNote.value = res.data.handleNote || ''
  processingStatus.value = res.data.status === 'PENDING' ? 'PROCESSING' : (res.data.status || 'PROCESSING')
  detailVisible.value = true
}

async function submitProcess() {
  if (!detail.value.id || processing.value) return

  processing.value = true
  try {
    const res = await api.processSupportTicket(detail.value.id, {
      status: processingStatus.value,
      handleNote: handleNote.value
    })
    if (!res.success || !res.data) {
      showToast(res.message || '更新失败')
      return
    }
    detail.value = res.data
    tickets.value = tickets.value.map((item) => item.id === res.data.id ? { ...item, ...res.data } : item)
    showToast('工单状态已更新')
  } catch (error) {
    showToast('更新失败，请稍后重试')
  } finally {
    processing.value = false
  }
}

onMounted(() => {
  loadTickets()
})
</script>

<style scoped>
.ticket-filters,
.ticket-filter-row,
.ticket-actions,
.ticket-process-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.ticket-filters {
  flex-direction: column;
  align-items: stretch;
}

.ticket-label {
  color: var(--text-muted);
  font-size: 14px;
}

.ticket-keyword-input {
  min-width: 320px;
  flex: 1;
}

.ticket-mono {
  font-family: monospace;
  font-size: 12px;
}

.ticket-title {
  font-weight: 600;
  color: var(--text-primary);
}

.ticket-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-muted);
}

.ticket-modal {
  width: min(760px, calc(100vw - 32px));
}

.ticket-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.ticket-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 18px;
  margin-bottom: 18px;
  color: var(--text-secondary);
}

.ticket-block {
  margin-bottom: 16px;
}

.ticket-block-title {
  margin-bottom: 8px;
  font-weight: 700;
  color: var(--text-primary);
}

.ticket-block-content {
  padding: 14px 16px;
  border-radius: 14px;
  background: rgba(255, 244, 246, 0.8);
  color: var(--text-secondary);
}

.ticket-content {
  white-space: pre-wrap;
  line-height: 1.7;
}

.ticket-textarea {
  min-height: 120px;
  resize: vertical;
}

.ticket-status-select {
  width: 160px;
}

@media (max-width: 960px) {
  .ticket-detail-grid {
    grid-template-columns: 1fr;
  }

  .ticket-keyword-input {
    min-width: 0;
    width: 100%;
  }
}
</style>
