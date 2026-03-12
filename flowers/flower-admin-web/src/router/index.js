import { createRouter, createWebHistory } from 'vue-router'
import { clearStoredAuth, getStoredAuth, hasActiveSession } from '../api'

const publicPaths = ['/login', '/register', '/change-password']

const routes = [
  {
    path: '/login',
    component: () => import('../views/console/Login.vue')
  },
  {
    path: '/register',
    component: () => import('../views/console/Register.vue')
  },
  {
    path: '/change-password',
    component: () => import('../views/console/ChangePassword.vue')
  },
  {
    path: '/',
    component: () => import('../layouts/ConsoleLayout.vue'),
    children: [
      { path: '', component: () => import('../views/console/Overview.vue') },
      { path: 'users', component: () => import('../views/console/Users.vue') },
      { path: 'merchants', component: () => import('../views/console/Merchants.vue') },
      { path: 'orders', component: () => import('../views/console/Orders.vue') },
      { path: 'reviews', component: () => import('../views/console/Reviews.vue') },
      { path: 'support-tickets', component: () => import('../views/console/SupportTickets.vue') },
      { path: 'config', component: () => import('../views/console/Config.vue') }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

router.beforeEach((to) => {
  const auth = getStoredAuth()

  if (publicPaths.includes(to.path)) {
    if ((to.path === '/login' || to.path === '/register') && auth?.role === 'SUPER_ADMIN') {
      return '/'
    }
    return true
  }

  if (!hasActiveSession()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (auth?.role !== 'SUPER_ADMIN') {
    clearStoredAuth()
    return { path: '/login' }
  }

  return true
})

export default router
