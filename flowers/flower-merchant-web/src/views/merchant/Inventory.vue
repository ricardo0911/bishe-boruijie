<template>
  <div>
    <div class="top-bar">
      <h1 class="page-title">库存管理</h1>
      <button class="btn btn-primary" @click="showStockInForm">+ 新增入库</button>
    </div>

    <div>
      <div v-for="a in outOfStockAlerts" :key="a.flowerName" class="alert-bar danger">
        &#9888; {{ a.flowerName }} 已缺货！可用库存：{{ a.availableQty }}
      </div>
    </div>

    <div class="card">
      <div class="card-header"><span class="card-title">库存预警</span></div>
      <div class="card-body">
        <div class="table-wrapper">
          <table>
            <thead><tr><th>花材</th><th>可用库存</th><th>预警阈值</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-if="alertsLoading"><td colspan="5" class="loading">加载中...</td></tr>
              <tr v-else-if="alerts.length === 0"><td colspan="5" class="empty">暂无花材数据</td></tr>
              <tr v-for="a in alerts" :key="a.flowerId" v-else>
                <td>{{ a.flowerName }}</td>
                <td>{{ a.availableQty }}</td>
                <td>{{ a.warnThreshold }}</td>
                <td>
                  <span
                    class="badge"
                    :class="
                      a.warningLevel === 'OUT_OF_STOCK'
                        ? 'badge-danger'
                        : a.warningLevel === 'LOW_STOCK'
                          ? 'badge-warning'
                          : 'badge-success'
                    "
                  >
                    {{
                      a.warningLevel === 'OUT_OF_STOCK'
                        ? '缺货'
                        : a.warningLevel === 'LOW_STOCK'
                          ? '低库存'
                          : '正常'
                    }}
                  </span>
                </td>
                <td><button class="btn btn-sm btn-outline" @click="viewBatches(a.flowerId)">查看批次</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-header">
        <span class="card-title">批次明细 (FEFO)</span>
        <div style="display:flex;gap:8px;align-items:center">
          <label style="font-size:13px;color:var(--text-secondary)">选择花材：</label>
          <select class="form-control" v-model="selectedFlowerId" @change="loadBatches" style="width:160px">
            <option value="">请选择</option>
            <option v-for="f in flowerOptions" :key="f.id" :value="f.id">{{ f.name }}</option>
          </select>
        </div>
      </div>
      <div class="card-body">
        <div class="table-wrapper">
          <table>
            <thead><tr><th>批次ID</th><th>供应商</th><th>品质</th><th>入库时间</th><th>凋谢时间</th><th>当前量</th><th>锁定量</th><th>可用量</th></tr></thead>
            <tbody>
              <tr v-if="!selectedFlowerId"><td colspan="8" class="empty">请选择花材</td></tr>
              <tr v-else-if="batchesLoading"><td colspan="8" class="loading">加载中...</td></tr>
              <tr v-else-if="batches.length === 0"><td colspan="8" class="empty">无批次数据</td></tr>
              <tr v-for="b in batches" :key="b.batchId" v-else>
                <td>{{ b.batchId }}</td>
                <td>{{ b.supplierName }}</td>
                <td><span class="badge" :class="b.qualityStatus === 'A' ? 'badge-success' : b.qualityStatus === 'B' ? 'badge-warning' : 'badge-danger'">{{ b.qualityStatus }}</span></td>
                <td>{{ b.receiptTime ? b.receiptTime.replace('T', ' ') : '-' }}</td>
                <td>{{ b.wiltTime ? b.wiltTime.replace('T', ' ') : '-' }}</td>
                <td>{{ b.currentQty }}</td>
                <td>{{ b.lockedQty }}</td>
                <td style="font-weight:600">{{ b.availableQty }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 入库表单弹窗 -->
    <div v-if="stockInVisible" class="modal">
      <div class="modal-overlay" @click="stockInVisible = false"></div>
      <div class="modal-content">
        <h2>新增入库</h2>
        <form @submit.prevent="saveStockIn">
          <div class="form-group">
            <label>花材</label>
            <select class="form-control" v-model="stockInForm.flowerId" required>
              <option value="">请选择花材</option>
              <option v-for="f in flowerOptions" :key="f.id" :value="f.id">{{ f.name }}</option>
            </select>
          </div>
          <div class="form-row">
            <div class="form-group"><label>入库数量</label><input type="number" class="form-control" v-model="stockInForm.quantity" min="1" required></div>
            <div class="form-group"><label>花期(天)</label><input type="number" class="form-control" v-model="stockInForm.shelfLifeDays" min="1"></div>
          </div>
          <div class="form-row">
            <div class="form-group"><label>供应商</label><input class="form-control" v-model="stockInForm.supplierName" placeholder="供应商名称"></div>
            <div class="form-group"><label>品质</label>
              <select class="form-control" v-model="stockInForm.qualityStatus">
                <option value="A">A 新鲜</option>
                <option value="B">B 轻微折损</option>
                <option value="C">C 临期促销</option>
              </select>
            </div>
          </div>
          <div class="form-actions">
            <button type="button" class="btn btn-outline" @click="stockInVisible = false">取消</button>
            <button type="submit" class="btn btn-primary">确认入库</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { api } from '../../api'
import { showToast } from '../../utils'

const alerts = ref([])
const alertsLoading = ref(true)
const flowerOptions = ref([])
const selectedFlowerId = ref('')
const batches = ref([])
const batchesLoading = ref(false)
const stockInVisible = ref(false)
const stockInForm = ref({ flowerId: '', quantity: '', shelfLifeDays: 7, supplierName: '', qualityStatus: 'A' })

const outOfStockAlerts = computed(() => alerts.value.filter(a => a.warningLevel === 'OUT_OF_STOCK'))

async function loadAlerts() {
  alertsLoading.value = true
  try {
    const flowersRes = await api.get('/flowers')
    const flowers = Array.isArray(flowersRes?.data) ? flowersRes.data : []
    const rows = await Promise.all(
      flowers.map(async (flower) => {
        try {
          const fefoRes = await api.get('/inventory/fefo/' + flower.id)
          const batches = Array.isArray(fefoRes?.data) ? fefoRes.data : []
          const availableQty = batches.reduce((sum, batch) => {
            const available = Number(
              batch.availableQty ?? (Number(batch.currentQty || 0) - Number(batch.lockedQty || 0))
            )
            return sum + (Number.isFinite(available) ? available : 0)
          }, 0)
          const warnThreshold = Number(flower.warn_threshold ?? flower.warnThreshold ?? 0)
          const warningLevel = availableQty <= 0
            ? 'OUT_OF_STOCK'
            : availableQty <= warnThreshold
              ? 'LOW_STOCK'
              : 'NORMAL'
          return {
            flowerId: flower.id,
            flowerName: flower.name,
            availableQty,
            warnThreshold,
            warningLevel
          }
        } catch (e) {
          return {
            flowerId: flower.id,
            flowerName: flower.name,
            availableQty: 0,
            warnThreshold: Number(flower.warn_threshold ?? flower.warnThreshold ?? 0),
            warningLevel: 'OUT_OF_STOCK'
          }
        }
      })
    )
    alerts.value = rows.sort((a, b) => a.availableQty - b.availableQty)
  } catch (e) { console.error(e) }
  alertsLoading.value = false
}

async function loadFlowerOptions() {
  try {
    const res = await api.get('/flowers')
    if (res.success) {
      flowerOptions.value = res.data
      if (!selectedFlowerId.value && flowerOptions.value.length > 0) {
        selectedFlowerId.value = flowerOptions.value[0].id
        loadBatches()
      }
    }
  } catch (e) { /* ignore */ }
}

function viewBatches(flowerId) {
  selectedFlowerId.value = flowerId
  loadBatches()
}

async function loadBatches() {
  if (!selectedFlowerId.value) return
  batchesLoading.value = true
  try {
    const res = await api.get('/inventory/fefo/' + selectedFlowerId.value)
    if (res.success) batches.value = res.data
    else batches.value = []
  } catch (e) { batches.value = [] }
  batchesLoading.value = false
}

function showStockInForm() {
  stockInForm.value = { flowerId: '', quantity: '', shelfLifeDays: 7, supplierName: '', qualityStatus: 'A' }
  stockInVisible.value = true
}

async function saveStockIn() {
  const body = {
    flowerId: stockInForm.value.flowerId,
    quantity: parseInt(stockInForm.value.quantity),
    shelfLifeDays: parseInt(stockInForm.value.shelfLifeDays),
    supplierName: stockInForm.value.supplierName,
    qualityStatus: stockInForm.value.qualityStatus
  }
  try {
    const res = await api.post('/inventory/batch', body)
    if (res.success) {
      showToast('入库成功')
      stockInVisible.value = false
      loadAlerts()
      if (selectedFlowerId.value === body.flowerId.toString()) loadBatches()
    } else {
      showToast('入库失败: ' + (res.message || ''))
    }
  } catch (err) { showToast('入库失败: ' + err.message) }
}

onMounted(() => {
  loadAlerts()
  loadFlowerOptions()
})
</script>
