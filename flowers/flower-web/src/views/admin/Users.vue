<template>
  <div>
    <div class="top-bar">
      <h1 class="page-title">用户管理</h1>
    </div>
    <div class="card">
      <div class="card-header"><span class="card-title">用户列表</span></div>
      <div class="card-body">
        <div class="table-wrapper">
          <table>
            <thead>
              <tr><th>ID</th><th>OpenID</th><th>姓名</th><th>手机</th><th>积分</th><th>偏好标签</th><th>注册时间</th><th>操作</th></tr>
            </thead>
            <tbody>
              <tr v-if="loading"><td colspan="8" class="loading">加载中...</td></tr>
              <tr v-else-if="users.length === 0"><td colspan="8" class="empty">暂无用户</td></tr>
              <tr v-for="u in users" :key="u.id" v-else>
                <td>{{ u.id }}</td>
                <td style="font-size:12px;color:var(--text-muted)">{{ u.openid }}</td>
                <td style="font-weight:500">{{ u.name }}</td>
                <td>{{ u.phone || '-' }}</td>
                <td><span class="badge badge-warning">{{ u.points }}</span></td>
                <td>
                  <template v-if="u.preference_tags">
                    <span v-for="tag in u.preference_tags.split(',')" :key="tag" class="badge badge-info" style="margin-right:4px">{{ tag }}</span>
                  </template>
                  <template v-else>-</template>
                </td>
                <td style="font-size:12px;color:var(--text-muted)">{{ u.created_at || '-' }}</td>
                <td><button class="btn btn-sm btn-outline" @click="viewUser(u.id)">详情</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 用户详情 -->
    <div v-if="detailVisible" class="card" style="margin-top:16px">
      <div class="card-header">
        <span class="card-title">用户详情</span>
        <button class="btn btn-sm btn-outline" @click="detailVisible = false">关闭</button>
      </div>
      <div class="card-body">
        <h3 style="margin-bottom:12px">最近订单</h3>
        <template v-if="userOrders.length > 0">
          <table style="width:100%">
            <thead><tr><th>订单号</th><th>商品</th><th>金额</th><th>状态</th><th>时间</th></tr></thead>
            <tbody>
              <tr v-for="o in userOrders" :key="o.orderNo">
                <td style="font-family:monospace;font-size:12px">{{ o.orderNo }}</td>
                <td>{{ o.items.map(i => i.productTitle + '×' + i.quantity).join(', ') }}</td>
                <td style="font-weight:600;color:var(--primary)">&yen;{{ formatPrice(o.totalAmount) }}</td>
                <td><span class="badge badge-info">{{ statusMap[o.status] || o.status }}</span></td>
                <td style="font-size:12px">{{ o.createdAt ? o.createdAt.replace('T', ' ').substring(0, 16) : '-' }}</td>
              </tr>
            </tbody>
          </table>
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

const statusMap = { CREATED: '已创建', LOCKED: '待支付', PAID: '已支付', CONFIRMED: '已确认', COMPLETED: '已完成', CANCELLED: '已取消', REFUNDED: '已退款' }

const users = ref([])
const loading = ref(true)
const detailVisible = ref(false)
const userOrders = ref([])

async function load() {
  loading.value = true
  try {
    const res = await api.get('/users')
    if (res.success && res.data) users.value = res.data
  } catch (e) { console.error(e) }
  loading.value = false
}

async function viewUser(userId) {
  try {
    const res = await api.get('/orders/user/' + userId + '/details?limit=10')
    if (res.success && res.data) userOrders.value = res.data
    else userOrders.value = []
    detailVisible.value = true
  } catch (e) { alert('加载详情失败') }
}

onMounted(load)
</script>
