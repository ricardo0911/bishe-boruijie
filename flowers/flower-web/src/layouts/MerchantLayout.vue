<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="sidebar-header">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 22c-4-3-8-6-8-10a4 4 0 0 1 8 0 4 4 0 0 1 8 0c0 4-4 7-8 10z" />
        </svg>
        花之都 · 商家中枢
      </div>

      <nav class="sidebar-nav">
        <router-link class="nav-item" to="/merchant" exact-active-class="active">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
          仪表盘
        </router-link>
        <router-link class="nav-item" to="/merchant/products" active-class="active">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"/><line x1="3" y1="6" x2="21" y2="6"/><path d="M16 10a4 4 0 0 1-8 0"/></svg>
          商品管理
        </router-link>
        <router-link class="nav-item" to="/merchant/flowers" active-class="active">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22c-4-3-8-6-8-10a4 4 0 0 1 8 0 4 4 0 0 1 8 0c0 4-4 7-8 10z"/></svg>
          花材管理
        </router-link>
        <router-link class="nav-item" to="/merchant/orders" active-class="active">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
          订单处理
        </router-link>
        <router-link class="nav-item" to="/merchant/inventory" active-class="active">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/></svg>
          库存管理
        </router-link>
        <router-link class="nav-item" to="/merchant/inventory-alert" active-class="active">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
          库存预警
        </router-link>
        <router-link class="nav-item" to="/merchant/sales-report" active-class="active">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 3v18h18"/><path d="M18.7 8l-5.1 5.2-2.8-2.7L7 14.3"/></svg>
          销售报表
        </router-link>
        <router-link class="nav-item" to="/merchant/template" active-class="active">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 4h16v16H4z"/><path d="M8 8h8"/><path d="M8 12h8"/><path d="M8 16h5"/></svg>
          卖花模板
        </router-link>
      </nav>
    </aside>

    <main class="main-content">
      <section class="brand-bar" aria-label="花之都商家品牌区">
        <div>
          <p class="brand-subtitle">HUAZHIDU MERCHANT STUDIO</p>
          <h2 class="brand-title">花之都 · 鲜花经营工作台</h2>
        </div>
        <div class="brand-pills">
          <span class="brand-pill">当前角色：商家</span>
          <span class="brand-pill">城市花艺</span>
          <span class="brand-pill">品质优先</span>
        </div>
      </section>
      <router-view />
    </main>
  </div>
</template>

<script setup>
</script>

<style scoped>
</style>
