<template>
  <div>
    <div class="top-bar">
      <h1 class="page-title">销售报表</h1>
      <div style="display:flex;gap:10px">
        <button class="btn btn-outline" @click="exportData('excel')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="7 10 12 15 17 10"/>
            <line x1="12" y1="15" x2="12" y2="3"/>
          </svg>
          导出Excel
        </button>
        <button class="btn btn-outline" @click="exportData('csv')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
            <line x1="16" y1="13" x2="8" y2="13"/>
            <line x1="16" y1="17" x2="8" y2="17"/>
            <polyline points="10 9 9 9 8 9"/>
          </svg>
          导出CSV
        </button>
      </div>
    </div>

    <!-- 日期筛选 -->
    <div class="card" style="margin-bottom:16px">
      <div class="card-body" style="padding:16px 20px">
        <div style="display:flex;gap:16px;flex-wrap:wrap;align-items:center">
          <div style="display:flex;align-items:center;gap:8px">
            <span style="font-size:13px;color:var(--text-secondary)">日期范围：</span>
            <input type="date" class="form-control" v-model="dateRange.start" style="width:140px">
            <span style="color:var(--text-muted)">至</span>
            <input type="date" class="form-control" v-model="dateRange.end" style="width:140px">
          </div>
          <div style="display:flex;align-items:center;gap:8px">
            <span style="font-size:13px;color:var(--text-secondary)">快速选择：</span>
            <button
              v-for="opt in quickDateOptions"
              :key="opt.value"
              class="btn btn-sm"
              :class="quickDate === opt.value ? 'btn-primary' : 'btn-outline'"
              @click="setQuickDate(opt.value)"
            >{{ opt.label }}</button>
          </div>
          <button class="btn btn-primary" @click="loadAllData" style="margin-left:auto">刷新数据</button>
        </div>
      </div>
    </div>

    <!-- 销售概览卡片 -->
    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-label">今日销售额</div>
        <div class="stat-value primary">{{ overview.todaySales != null ? '¥' + formatPrice(overview.todaySales) : '-' }}</div>
        <div class="stat-change" :class="getChangeClass(overview.todaySalesChange)">
          {{ formatChange(overview.todaySalesChange) }}
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">本周销售额</div>
        <div class="stat-value">{{ overview.weekSales != null ? '¥' + formatPrice(overview.weekSales) : '-' }}</div>
        <div class="stat-change" :class="getChangeClass(overview.weekSalesChange)">
          {{ formatChange(overview.weekSalesChange) }}
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">本月销售额</div>
        <div class="stat-value primary">{{ overview.monthSales != null ? '¥' + formatPrice(overview.monthSales) : '-' }}</div>
        <div class="stat-change" :class="getChangeClass(overview.monthSalesChange)">
          {{ formatChange(overview.monthSalesChange) }}
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">订单总数</div>
        <div class="stat-value success">{{ overview.totalOrders ?? '-' }}</div>
        <div class="stat-change" :class="getChangeClass(overview.ordersChange)">
          {{ formatChange(overview.ordersChange) }}
        </div>
      </div>
    </div>

    <!-- 销售趋势图表 -->
    <div class="card">
      <div class="card-header">
        <span class="card-title">销售趋势</span>
        <div style="display:flex;gap:8px">
          <button
            v-for="period in periodOptions"
            :key="period.value"
            class="btn btn-sm"
            :class="currentPeriod === period.value ? 'btn-primary' : 'btn-outline'"
            @click="changePeriod(period.value)"
          >{{ period.label }}</button>
        </div>
      </div>
      <div class="card-body">
        <div ref="trendChartRef" class="chart-container"></div>
      </div>
    </div>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:24px">
      <!-- 热销商品排行 -->
      <div class="card">
        <div class="card-header">
          <span class="card-title">热销商品 TOP10</span>
          <span style="font-size:13px;color:var(--text-muted)">按销售额排序</span>
        </div>
        <div class="card-body" style="padding:0">
          <div class="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th style="width:50px;text-align:center">排名</th>
                  <th>商品名称</th>
                  <th style="text-align:right">销量</th>
                  <th style="text-align:right">销售额</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="topProductsLoading"><td colspan="4" class="loading">加载中...</td></tr>
                <tr v-else-if="topProducts.length === 0"><td colspan="4" class="empty">暂无数据</td></tr>
                <tr v-for="(item, index) in topProducts" :key="item.productId" v-else>
                  <td style="text-align:center">
                    <span class="rank-badge" :class="getRankClass(index + 1)">{{ index + 1 }}</span>
                  </td>
                  <td>
                    <div style="display:flex;align-items:center;gap:8px">
                      <img v-if="item.productImage" :src="resolveImageUrl(item.productImage)" style="width:40px;height:40px;object-fit:cover;border-radius:4px">
                      <span style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:180px">{{ item.productName }}</span>
                    </div>
                  </td>
                  <td style="text-align:right">{{ item.salesCount }}</td>
                  <td style="text-align:right;font-weight:600;color:var(--primary)">¥{{ formatPrice(item.salesAmount) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- 品类销售占比 -->
      <div class="card">
        <div class="card-header">
          <span class="card-title">品类销售占比</span>
          <span style="font-size:13px;color:var(--text-muted)">按销售额分布</span>
        </div>
        <div class="card-body">
          <div ref="categoryChartRef" class="chart-container" style="height:320px"></div>
          <div v-if="categoryLoading" class="loading" style="position:absolute;inset:0;background:rgba(255,255,255,0.8);display:flex;align-items:center;justify-content:center">加载中...</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { api } from '../../api'
import { formatPrice, showToast, resolveImageUrl } from '../../utils'

// 日期范围
const dateRange = ref({
  start: '',
  end: ''
})
const quickDate = ref('week')

const quickDateOptions = [
  { value: 'today', label: '今日' },
  { value: 'week', label: '本周' },
  { value: 'month', label: '本月' },
  { value: 'quarter', label: '本季度' }
]

// 销售概览
const overview = ref({})
const overviewLoading = ref(false)

// 销售趋势
const trendChartRef = ref(null)
const trendData = ref([])
const trendLoading = ref(false)
const currentPeriod = ref('daily')
const periodOptions = [
  { value: 'daily', label: '按日' },
  { value: 'weekly', label: '按周' },
  { value: 'monthly', label: '按月' }
]

// 热销商品
const topProducts = ref([])
const topProductsLoading = ref(false)

// 品类分析
const categoryChartRef = ref(null)
const categoryData = ref([])
const categoryLoading = ref(false)

// ECharts实例
let trendChart = null
let categoryChart = null

// 设置快速日期
function setQuickDate(type) {
  quickDate.value = type
  const today = new Date()
  const end = new Date(today)
  let start = new Date(today)

  switch (type) {
    case 'today':
      start = new Date(today)
      break
    case 'week':
      start.setDate(today.getDate() - 7)
      break
    case 'month':
      start.setMonth(today.getMonth() - 1)
      break
    case 'quarter':
      start.setMonth(today.getMonth() - 3)
      break
  }

  dateRange.value.start = formatDate(start)
  dateRange.value.end = formatDate(end)
  loadAllData()
}

function formatDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

// 加载所有数据
async function loadAllData() {
  await Promise.all([
    loadOverview(),
    loadTrend(),
    loadTopProducts(),
    loadCategoryData()
  ])
}

// 加载销售概览
async function loadOverview() {
  overviewLoading.value = true
  try {
    const params = new URLSearchParams()
    if (dateRange.value.start) params.append('startDate', dateRange.value.start)
    if (dateRange.value.end) params.append('endDate', dateRange.value.end)

    const res = await api.get('/stats/sales/overview?' + params.toString())
    if (res.success && res.data) {
      overview.value = {
        todaySales: res.data.today?.salesAmount ?? 0,
        weekSales: res.data.week?.salesAmount ?? 0,
        monthSales: res.data.month?.salesAmount ?? 0,
        totalOrders: res.data.month?.orderCount ?? 0,
        todaySalesChange: null,
        weekSalesChange: null,
        monthSalesChange: null,
        ordersChange: null
      }
    }
  } catch (e) {
    console.error('加载销售概览失败:', e)
  }
  overviewLoading.value = false
}

// 加载销售趋势
async function loadTrend() {
  trendLoading.value = true
  try {
    const params = new URLSearchParams()
    params.append('period', currentPeriod.value)
    if (dateRange.value.start) params.append('startDate', dateRange.value.start)
    if (dateRange.value.end) params.append('endDate', dateRange.value.end)

    const res = await api.get('/stats/sales/trend?' + params.toString())
    if (res.success && res.data) {
      trendData.value = Array.isArray(res.data.data) ? res.data.data : []
      renderTrendChart()
    }
  } catch (e) {
    console.error('加载销售趋势失败:', e)
  }
  trendLoading.value = false
}

// 切换周期
function changePeriod(period) {
  currentPeriod.value = period
  loadTrend()
}

// 加载热销商品
async function loadTopProducts() {
  topProductsLoading.value = true
  try {
    const params = new URLSearchParams()
    params.append('limit', '10')
    if (dateRange.value.start) params.append('startDate', dateRange.value.start)
    if (dateRange.value.end) params.append('endDate', dateRange.value.end)

    const res = await api.get('/stats/products/top?' + params.toString())
    if (res.success && res.data) {
      const products = Array.isArray(res.data.products) ? res.data.products : []
      topProducts.value = products.map((item) => ({
        productId: item.productId,
        productName: item.productName || item.productTitle || item.title || (item.productId ? `商品#${item.productId}` : '未命名商品'),
        productImage: '',
        salesCount: item.totalQuantity,
        salesAmount: item.totalSales
      }))
    }
  } catch (e) {
    console.error('加载热销商品失败:', e)
  }
  topProductsLoading.value = false
}

// 加载品类数据
async function loadCategoryData() {
  categoryLoading.value = true
  try {
    const params = new URLSearchParams()
    if (dateRange.value.start) params.append('startDate', dateRange.value.start)
    if (dateRange.value.end) params.append('endDate', dateRange.value.end)

    const res = await api.get('/stats/categories?' + params.toString())
    if (res.success && res.data) {
      categoryData.value = Array.isArray(res.data.categories) ? res.data.categories : []
      renderCategoryChart()
    }
  } catch (e) {
    console.error('加载品类数据失败:', e)
  }
  categoryLoading.value = false
}

// 渲染销售趋势图表
async function renderTrendChart() {
  if (!trendChartRef.value) return

  // 动态导入ECharts
  const echarts = await import('echarts')

  if (trendChart) {
    trendChart.dispose()
  }

  trendChart = echarts.init(trendChartRef.value)

  const dates = trendData.value.map(item => item.date || item.period)
  const sales = trendData.value.map(item => item.salesAmount || item.amount || 0)
  const orders = trendData.value.map(item => item.orderCount || item.orders || 0)

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross'
      }
    },
    legend: {
      data: ['销售额', '订单数'],
      bottom: 0
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '15%',
      top: '10%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: {
        lineStyle: {
          color: '#EEDDE4'
        }
      },
      axisLabel: {
        color: '#685761'
      }
    },
    yAxis: [
      {
        type: 'value',
        name: '销售额(¥)',
        position: 'left',
        axisLine: {
          show: false
        },
        axisLabel: {
          color: '#685761',
          formatter: (value) => {
            if (value >= 10000) return (value / 10000).toFixed(1) + '万'
            return value
          }
        },
        splitLine: {
          lineStyle: {
            color: '#F7F2F5'
          }
        }
      },
      {
        type: 'value',
        name: '订单数',
        position: 'right',
        axisLine: {
          show: false
        },
        axisLabel: {
          color: '#685761'
        },
        splitLine: {
          show: false
        }
      }
    ],
    series: [
      {
        name: '销售额',
        type: 'line',
        smooth: true,
        data: sales,
        itemStyle: {
          color: '#D8647F'
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(216, 100, 127, 0.3)' },
            { offset: 1, color: 'rgba(216, 100, 127, 0.05)' }
          ])
        }
      },
      {
        name: '订单数',
        type: 'bar',
        yAxisIndex: 1,
        data: orders,
        itemStyle: {
          color: '#4E74D8',
          borderRadius: [4, 4, 0, 0]
        },
        barWidth: '30%'
      }
    ]
  }

  trendChart.setOption(option)
}

// 渲染品类占比图表
async function renderCategoryChart() {
  if (!categoryChartRef.value) return

  // 动态导入ECharts
  const echarts = await import('echarts')

  if (categoryChart) {
    categoryChart.dispose()
  }

  categoryChart = echarts.init(categoryChartRef.value)

  const data = categoryData.value.map(item => ({
    name: item.category || item.categoryName || item.name,
    value: item.salesAmount || item.amount || 0
  }))

  const colors = ['#D8647F', '#4E74D8', '#3C9A6D', '#D38A3A', '#9B59B6', '#1ABC9C', '#E74C3C', '#3498DB']

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: ¥{c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: '5%',
      top: 'center',
      textStyle: {
        color: '#685761'
      }
    },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 8,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: false
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 14,
            fontWeight: 'bold'
          }
        },
        labelLine: {
          show: false
        },
        data: data,
        color: colors
      }
    ]
  }

  categoryChart.setOption(option)
}

// 获取排名样式
function getRankClass(rank) {
  if (rank === 1) return 'rank-1'
  if (rank === 2) return 'rank-2'
  if (rank === 3) return 'rank-3'
  return ''
}

// 格式化变化率
function formatChange(value) {
  if (value == null || value === undefined) return ''
  const prefix = value >= 0 ? '+' : ''
  return `${prefix}${value}% 环比`
}

function getChangeClass(value) {
  if (value == null || value === undefined) return ''
  return value >= 0 ? 'positive' : 'negative'
}

// 导出数据
async function exportData(type) {
  try {
    // 准备导出数据
    const exportData = {
      overview: overview.value,
      trend: trendData.value,
      topProducts: topProducts.value,
      categories: categoryData.value
    }

    if (type === 'excel') {
      // 尝试调用后端导出API
      const params = new URLSearchParams()
      params.append('type', 'excel')
      if (dateRange.value.start) params.append('startDate', dateRange.value.start)
      if (dateRange.value.end) params.append('endDate', dateRange.value.end)

      try {
        const res = await api.get('/stats/export?' + params.toString())
        if (res.success && res.data) {
          downloadFile(res.data, `销售报表_${dateRange.value.start}_${dateRange.value.end}.xlsx`, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
          showToast('导出成功')
          return
        }
      } catch (e) {
        console.log('后端导出失败，使用前端导出')
      }
    }

    // 前端导出CSV
    exportAsCsv(exportData)
  } catch (e) {
    showToast('导出失败: ' + e.message)
  }
}

function exportAsCsv(data) {
  let csv = '销售报表\n\n'

  // 概览数据
  csv += '销售概览\n'
  csv += '指标,数值\n'
  csv += `今日销售额,${overview.value.todaySales || 0}\n`
  csv += `本周销售额,${overview.value.weekSales || 0}\n`
  csv += `本月销售额,${overview.value.monthSales || 0}\n`
  csv += `订单总数,${overview.value.totalOrders || 0}\n\n`

  // 销售趋势
  csv += '销售趋势\n'
  csv += '日期,销售额,订单数\n'
  trendData.value.forEach(item => {
    csv += `${item.date || item.period},${item.salesAmount || item.amount || 0},${item.orderCount || item.orders || 0}\n`
  })
  csv += '\n'

  // 热销商品
  csv += '热销商品TOP10\n'
  csv += '排名,商品名称,销量,销售额\n'
  topProducts.value.forEach((item, index) => {
    csv += `${index + 1},"${item.productName}",${item.salesCount},${item.salesAmount}\n`
  })
  csv += '\n'

  // 品类占比
  csv += '品类销售占比\n'
  csv += '品类,销售额,占比\n'
  const totalCategorySales = categoryData.value.reduce((sum, item) => sum + (item.salesAmount || item.amount || 0), 0)
  categoryData.value.forEach(item => {
    const amount = item.salesAmount || item.amount || 0
    const percentage = totalCategorySales > 0 ? ((amount / totalCategorySales) * 100).toFixed(2) : 0
    csv += `"${item.category || item.categoryName || item.name}",${amount},${percentage}%\n`
  })

  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' })
  const fileName = `销售报表_${dateRange.value.start || 'all'}_${dateRange.value.end || 'all'}.csv`
  downloadFile(blob, fileName, 'text/csv;charset=utf-8;')
  showToast('CSV导出成功')
}

function downloadFile(data, fileName, mimeType) {
  let blob
  if (data instanceof Blob) {
    blob = data
  } else if (typeof data === 'string' && data.startsWith('data:')) {
    // Base64数据
    const byteString = atob(data.split(',')[1])
    const ab = new ArrayBuffer(byteString.length)
    const ia = new Uint8Array(ab)
    for (let i = 0; i < byteString.length; i++) {
      ia[i] = byteString.charCodeAt(i)
    }
    blob = new Blob([ab], { type: mimeType })
  } else {
    blob = new Blob([data], { type: mimeType })
  }

  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

// 响应式处理
function handleResize() {
  trendChart && trendChart.resize()
  categoryChart && categoryChart.resize()
}

onMounted(() => {
  // 设置默认日期范围为本周
  setQuickDate('week')

  // 监听窗口大小变化
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  trendChart && trendChart.dispose()
  categoryChart && categoryChart.dispose()
})
</script>

<style scoped>
.chart-container {
  width: 100%;
  height: 350px;
}

.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 600;
  background: #F7F2F5;
  color: var(--text-secondary);
}

.rank-badge.rank-1 {
  background: linear-gradient(135deg, #FFD700 0%, #FFA500 100%);
  color: #fff;
}

.rank-badge.rank-2 {
  background: linear-gradient(135deg, #C0C0C0 0%, #A0A0A0 100%);
  color: #fff;
}

.rank-badge.rank-3 {
  background: linear-gradient(135deg, #CD7F32 0%, #B87333 100%);
  color: #fff;
}

.stat-change {
  font-size: 12px;
  margin-top: 4px;
  color: var(--text-muted);
}

.stat-change.positive {
  color: var(--success);
}

.stat-change.negative {
  color: var(--danger);
}

@media (max-width: 1024px) {
  .chart-container {
    height: 280px;
  }
}

@media (max-width: 768px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .chart-container {
    height: 240px;
  }
}
</style>
