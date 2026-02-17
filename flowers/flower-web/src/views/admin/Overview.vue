<template>
  <div>
    <div class="top-bar">
      <h1 class="page-title">系统概览</h1>
    </div>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-label">注册用户</div>
        <div class="stat-value primary">{{ stats.totalUsers ?? '-' }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">在售商品</div>
        <div class="stat-value">{{ stats.totalProducts ?? '-' }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">今日订单</div>
        <div class="stat-value success">{{ stats.todayOrders ?? '-' }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">今日销售额</div>
        <div class="stat-value primary">{{ stats.todaySales != null ? '¥' + formatPrice(stats.todaySales) : '-' }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">本周订单</div>
        <div class="stat-value">{{ stats.weekOrders ?? '-' }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">本周销售额</div>
        <div class="stat-value">{{ stats.weekSales != null ? '¥' + formatPrice(stats.weekSales) : '-' }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">待处理订单</div>
        <div class="stat-value warning">{{ stats.pendingOrders ?? '-' }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">库存预警</div>
        <div class="stat-value danger">{{ stats.inventoryAlerts ?? '-' }}</div>
      </div>
    </div>

    <div class="card">
      <div class="card-header"><span class="card-title">快捷入口</span></div>
      <div class="card-body" style="display:flex;gap:12px;flex-wrap:wrap">
        <router-link to="/admin/users" class="btn btn-outline">用户管理</router-link>
        <router-link to="/admin/merchants" class="btn btn-outline">商家管理</router-link>
        <router-link to="/admin/config" class="btn btn-outline">系统配置</router-link>
        <router-link to="/merchant" class="btn btn-outline">商家后台</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../../api'
import { formatPrice } from '../../utils'

const stats = ref({})

onMounted(async () => {
  try {
    const res = await api.get('/stats/dashboard')
    if (res.success && res.data) stats.value = res.data
  } catch (e) { console.error(e) }
})
</script>
