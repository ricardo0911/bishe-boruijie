import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/merchant',
    component: () => import('../layouts/MerchantLayout.vue'),
    children: [
      { path: '', component: () => import('../views/merchant/Dashboard.vue') },
      { path: 'products', component: () => import('../views/merchant/Products.vue') },
      { path: 'flowers', component: () => import('../views/merchant/Flowers.vue') },
      { path: 'orders', component: () => import('../views/merchant/Orders.vue') },
      { path: 'inventory', component: () => import('../views/merchant/Inventory.vue') }
    ]
  },
  {
    path: '/admin',
    component: () => import('../layouts/AdminLayout.vue'),
    children: [
      { path: '', component: () => import('../views/admin/Overview.vue') },
      { path: 'users', component: () => import('../views/admin/Users.vue') },
      { path: 'merchants', component: () => import('../views/admin/Merchants.vue') },
      { path: 'config', component: () => import('../views/admin/Config.vue') }
    ]
  },
  { path: '/', redirect: '/merchant' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
