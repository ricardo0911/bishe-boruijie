<template>
  <div>
    <div class="top-bar">
      <h1 class="page-title">用户管理</h1>
    </div>

    <div class="card">
      <div class="card-header">
        <span class="card-title">用户列表</span>
      </div>
      <div class="card-body">
        <div class="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>OpenID</th>
                <th>用户名称</th>
                <th>手机号</th>
                <th>积分</th>
                <th>注册时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="7" class="loading">加载中...</td>
              </tr>
              <tr v-else-if="users.length === 0">
                <td colspan="7" class="empty">暂无用户数据</td>
              </tr>
              <tr v-for="user in users" :key="user.id" v-else>
                <td>{{ user.id }}</td>
                <td style="font-size:12px;color:var(--text-muted)">{{ user.openid || '-' }}</td>
                <td style="font-weight:500">{{ displayText(user.name) }}</td>
                <td>{{ user.phone || '-' }}</td>
                <td>
                  <span class="badge badge-warning">{{ user.points ?? 0 }}</span>
                </td>
                <td style="font-size:12px;color:var(--text-muted)">{{ formatTime(user.createdAt || user.created_at) }}</td>
                <td>
                  <button class="btn btn-sm btn-outline" @click="viewUser(user.id)">详情</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div v-if="detailVisible" class="card" style="margin-top:16px">
      <div class="card-header">
        <span class="card-title">用户最近订单</span>
        <button class="btn btn-sm btn-outline" @click="detailVisible = false">关闭</button>
      </div>
      <div class="card-body">
        <template v-if="userOrders.length > 0">
          <div class="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>订单号</th>
                  <th>商品</th>
                  <th>金额</th>
                  <th>状态</th>
                  <th>时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="order in userOrders" :key="order.orderNo">
                  <td style="font-family:monospace;font-size:12px">{{ order.orderNo }}</td>
                  <td>{{ getItemNames(order) }}</td>
                  <td style="font-weight:600;color:var(--primary)">¥{{ formatPrice(order.totalAmount || 0) }}</td>
                  <td>
                    <span class="badge badge-info">{{ statusMap[order.status] || order.status || '-' }}</span>
                  </td>
                  <td style="font-size:12px;color:var(--text-muted)">{{ formatTime(order.createdAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
        <div v-else class="empty">该用户暂无订单</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../../api'
import { formatPrice } from '../../utils'

const statusMap = {
  CREATED: '已创建',
  LOCKED: '待支付',
  PAID: '已支付',
  CONFIRMED: '已发货',
  REFUND_REQUESTED: '退款申请中',
  REFUNDING: '退款处理中',
  REFUND_FAILED: '退款失败',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REFUNDED: '已退款'
}

const users = ref([])
const loading = ref(true)
const detailVisible = ref(false)
const userOrders = ref([])

function formatTime(value) {
  return value ? String(value).replace('T', ' ').substring(0, 16) : '-'
}

function resolveOrderItemName(item) {
  if (!item) return '未命名商品'
  return item.productTitle || item.productName || item.title || (item.productId ? `商品#${item.productId}` : '未命名商品')
}

function getItemNames(order) {
  if (!order.items || order.items.length === 0) return '-'
  return order.items.map((item) => `${resolveOrderItemName(item)} x${item.quantity}`).join('、')
}

function displayText(text) {
  return text || '-'
}

async function load() {
  loading.value = true
  try {
    const res = await api.get('/users')
    users.value = res.success && Array.isArray(res.data) ? res.data : []
  } catch (error) {
    users.value = []
    console.error(error)
  }
  loading.value = false
}

async function viewUser(userId) {
  try {
    const res = await api.get('/orders/user/' + userId + '/details?limit=10')
    userOrders.value = res.success && Array.isArray(res.data) ? res.data : []
    detailVisible.value = true
  } catch (error) {
    alert('加载订单详情失败')
  }
}

onMounted(load)
</script>