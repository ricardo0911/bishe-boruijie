import { createRouter, createWebHistory } from 'vue-router'
import { clearStoredAuth, getStoredAuth, hasActiveSession } from '../api'

const publicPaths = ['/merchant/login', '/merchant/register', '/merchant/change-password']

const routes = [
  { path: '/merchant/login', component: () => import('../views/merchant/Login.vue') },
  { path: '/merchant/register', component: () => import('../views/merchant/Register.vue') },
  { path: '/merchant/change-password', component: () => import('../views/merchant/ChangePassword.vue') },
  {
    path: '/merchant',
    component: () => import('../layouts/MerchantLayout.vue'),
    children: [
      { path: '', component: () => import('../views/merchant/Dashboard.vue') },
      { path: 'products', component: () => import('../views/merchant/Products.vue') },
      { path: 'flowers', component: () => import('../views/merchant/Flowers.vue') },
      { path: 'orders', component: () => import('../views/merchant/Orders.vue') },
      { path: 'reviews', component: () => import('../views/merchant/Reviews.vue') },
      { path: 'after-sales', component: () => import('../views/merchant/AfterSales.vue') },
      { path: 'inventory', component: () => import('../views/merchant/Inventory.vue') },
      { path: 'inventory-alert', component: () => import('../views/merchant/InventoryAlert.vue') },
      { path: 'sales-report', component: () => import('../views/merchant/SalesReport.vue') }
    ]
  },
  { path: '/', redirect: '/merchant' },
  { path: '/:pathMatch(.*)*', redirect: '/merchant' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = getStoredAuth()

  if (publicPaths.includes(to.path)) {
    if ((to.path === '/merchant/login' || to.path === '/merchant/register') && auth?.role === 'MERCHANT') {
      return '/merchant'
    }
    return true
  }

  if (!hasActiveSession()) {
    return { path: '/merchant/login', query: { redirect: to.fullPath } }
  }

  if (auth?.role !== 'MERCHANT') {
    clearStoredAuth()
    return { path: '/merchant/login' }
  }

  return true
})

export default router
