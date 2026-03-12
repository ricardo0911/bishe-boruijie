<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="sidebar-header">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 22c-4-3-8-6-8-10a4 4 0 0 1 8 0 4 4 0 0 1 8 0c0 4-4 7-8 10z" />
        </svg>
        花之都 · 管理中枢
      </div>

      <nav class="sidebar-nav">
        <router-link class="nav-item" to="/" exact-active-class="active">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7" /><rect x="14" y="3" width="7" height="7" /><rect x="14" y="14" width="7" height="7" /><rect x="3" y="14" width="7" height="7" /></svg>
          系统概览
        </router-link>
        <router-link class="nav-item" to="/users" active-class="active">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M23 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" /></svg>
          用户管理
        </router-link>
        <router-link class="nav-item" to="/merchants" active-class="active">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" /><polyline points="9 22 9 12 15 12 15 22" /></svg>
          商家管理
        </router-link>
        <router-link class="nav-item" to="/orders" active-class="active">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="16" rx="2" /><path d="M7 8h10" /><path d="M7 12h10" /><path d="M7 16h6" /></svg>
          订单管理
        </router-link>
        <router-link class="nav-item" to="/reviews" active-class="active">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" /><path d="M8 9h8" /><path d="M8 13h4" /></svg>
          评论管理
        </router-link>
        <router-link class="nav-item" to="/support-tickets" active-class="active">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" /><path d="M8 9h8" /><path d="M8 13h5" /></svg>
          反馈管理
        </router-link>
        <router-link class="nav-item" to="/config" active-class="active">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" /></svg>
          系统配置
        </router-link>
      </nav>
    </aside>

    <main class="main-content">
      <section class="brand-bar" aria-label="花之都管理端品牌区">
        <div>
          <p class="brand-subtitle">FLOWER CAPITAL CONSOLE</p>
          <h2 class="brand-title">花之都 · 城市花艺运营台</h2>
        </div>

        <div class="brand-tools">
          <div class="brand-pills">
            <span class="brand-pill">当前账号：{{ displayName }}</span>
            <span class="brand-pill">角色：管理员</span>
          </div>
          <router-link class="btn btn-outline" to="/change-password">修改密码</router-link>
          <button class="btn btn-outline" @click="logout">退出登录</button>
        </div>
      </section>

      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { api, clearStoredAuth, getStoredAuth } from '../api'

const router = useRouter()
const displayName = computed(() => getStoredAuth()?.displayName || getStoredAuth()?.account || '系统管理员')

async function logout() {
  try {
    await api.logout()
  } finally {
    clearStoredAuth()
    router.replace('/login')
  }
}
</script>

<style scoped>
.brand-tools {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
</style>
