<template>
  <div>
    <div class="top-bar">
      <h1 class="page-title">系统配置</h1>
    </div>
    <div class="card">
      <div class="card-header">
        <span class="card-title">参数配置</span>
        <span style="font-size:12px;color:var(--text-muted)">来自 system_config 表</span>
      </div>
      <div class="card-body">
        <div class="table-wrapper">
          <table>
            <thead>
              <tr><th>配置项</th><th>值</th><th>说明</th><th>操作</th></tr>
            </thead>
            <tbody>
              <tr v-if="loading"><td colspan="4" class="loading">加载中...</td></tr>
              <tr v-else-if="allConfigs.length === 0"><td colspan="4" class="empty">暂无配置数据</td></tr>
              <tr v-for="c in allConfigs" :key="c.id" v-else>
                <td style="font-weight:500;font-family:monospace">{{ c.configKey }}</td>
                <td><input class="form-control" v-model="c.configValue" style="max-width:200px"></td>
                <td style="color:var(--text-muted);font-size:12px">{{ c.description || '-' }}</td>
                <td><button class="btn btn-sm btn-primary" @click="saveConfig(c.id, c.configValue)">保存</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-header"><span class="card-title">系统信息</span></div>
      <div class="card-body">
        <div class="table-wrapper">
          <table>
            <tbody>
              <tr><td style="font-weight:500;width:200px">后端框架</td><td>Spring Boot 3.3.8</td></tr>
              <tr><td style="font-weight:500">数据库</td><td>MySQL 8.x (flower_shop)</td></tr>
              <tr><td style="font-weight:500">Java 版本</td><td>JDK 17</td></tr>
              <tr><td style="font-weight:500">API 文档</td><td><a href="http://localhost:8080/swagger-ui/index.html" target="_blank" style="color:var(--primary)">Swagger UI</a></td></tr>
              <tr><td style="font-weight:500">健康检查</td><td><a href="http://localhost:8080/actuator/health" target="_blank" style="color:var(--primary)">/actuator/health</a></td></tr>
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
import { showToast } from '../../utils'

const allConfigs = ref([])
const loading = ref(true)

async function loadConfigs() {
  loading.value = true
  try {
    const res = await api.get('/system-config')
    if (res.success && Array.isArray(res.data)) allConfigs.value = res.data
    else allConfigs.value = []
  } catch (e) { allConfigs.value = [] }
  loading.value = false
}

async function saveConfig(id, value) {
  try {
    await api.put('/system-config/' + id, { configValue: value })
    showToast('保存成功')
    loadConfigs()
  } catch (err) { showToast('保存失败: ' + err.message) }
}

onMounted(loadConfigs)
</script>
