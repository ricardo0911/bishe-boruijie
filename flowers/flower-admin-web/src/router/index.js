import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    component: () => import('../layouts/AdminLayout.vue'),
    children: [
      { path: '', component: () => import('../views/admin/Overview.vue') },
      { path: 'users', component: () => import('../views/admin/Users.vue') },
      { path: 'merchants', component: () => import('../views/admin/Merchants.vue') },
      { path: 'orders', component: () => import('../views/admin/Orders.vue') },
      { path: 'config', component: () => import('../views/admin/Config.vue') }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

export default router
