<template>
  <div>
    <div class="top-bar">
      <h1 class="page-title">仪表盘</h1>
    </div>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-label">今日订单</div>
        <div class="stat-value primary">{{ stats.todayOrders ?? '-' }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">今日销售额</div>
        <div class="stat-value">{{ stats.todaySales != null ? '¥' + formatPrice(stats.todaySales) : '-' }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">待处理订单</div>
        <div class="stat-value warning">{{ stats.pendingOrders ?? '-' }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">库存预警</div>
        <div class="stat-value danger">{{ stats.inventoryAlerts ?? '-' }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">本周订单</div>
        <div class="stat-value">{{ stats.weekOrders ?? '-' }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">本周销售额</div>
        <div class="stat-value primary">{{ stats.weekSales != null ? '¥' + formatPrice(stats.weekSales) : '-' }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">在售商品</div>
        <div class="stat-value">{{ stats.totalProducts ?? '-' }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">花材种类</div>
        <div class="stat-value">{{ stats.totalFlowers ?? '-' }}</div>
      </div>
    </div>

    <div class="card">
      <div class="card-header"><span class="card-title">库存预警</span></div>
      <div class="card-body">
        <div class="table-wrapper">
          <table>
            <thead>
              <tr><th>花材</th><th>可用库存</th><th>预警阈值</th><th>状态</th></tr>
            </thead>
            <tbody>
              <tr v-if="alertsLoading"><td colspan="4" class="loading">加载中...</td></tr>
              <tr v-else-if="alerts.length === 0"><td colspan="4" class="empty">暂无预警</td></tr>
              <tr v-for="a in alerts" :key="a.flowerId" v-else>
                <td>{{ a.flowerName }}</td>
                <td>{{ a.availableQty }}</td>
                <td>{{ a.warnThreshold }}</td>
                <td><span class="badge" :class="a.warningLevel === 'OUT_OF_STOCK' ? 'badge-danger' : 'badge-warning'">{{ a.warningLevel === 'OUT_OF_STOCK' ? '缺货' : '低库存' }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-header"><span class="card-title">补货建议</span></div>
      <div class="card-body">
        <div class="table-wrapper">
          <table>
            <thead>
              <tr><th>花材</th><th>预测需求</th><th>在手库存</th><th>建议补货</th><th>状态</th></tr>
            </thead>
            <tbody>
              <tr v-if="restockLoading"><td colspan="5" class="loading">加载中...</td></tr>
              <tr v-else-if="restock.length === 0"><td colspan="5" class="empty">暂无建议</td></tr>
              <tr v-for="r in restock" :key="r.flowerName" v-else>
                <td>{{ r.flowerName }}</td>
                <td>{{ r.predictedDemand }}</td>
                <td>{{ r.onHand }}</td>
                <td :style="{ fontWeight: 600, color: parseFloat(r.suggestedQty) > 0 ? 'var(--danger)' : 'var(--success)' }">{{ r.suggestedQty }}</td>
                <td><span class="badge" :class="r.status === 'NEW' ? 'badge-info' : 'badge-default'">{{ r.status }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../../api'
import { formatPrice } from '../../utils'

const stats = ref({})
const alerts = ref([])
const restock = ref([])
const alertsLoading = ref(true)
const restockLoading = ref(true)

onMounted(async () => {
  try {
    const [statsRes, alertsRes, restockRes] = await Promise.all([
      api.get('/stats/dashboard'),
      api.get('/inventory/alerts'),
      api.get('/analysis/replenishment').catch(() => ({ success: false }))
    ])
    if (statsRes.success && statsRes.data) stats.value = statsRes.data
    if (alertsRes.success) alerts.value = alertsRes.data
    alertsLoading.value = false
    if (restockRes.success) restock.value = restockRes.data
    restockLoading.value = false
  } catch (e) {
    console.error(e)
    alertsLoading.value = false
    restockLoading.value = false
  }
})
</script>
