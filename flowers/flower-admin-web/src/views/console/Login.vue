<template>
  <div class="login-shell">
    <div class="login-panel card">
      <div class="login-copy">
        <p class="login-kicker">FLOWER CAPITAL CONSOLE</p>
        <h1 class="login-title">管理员登录</h1>
        <p class="login-subtitle">
          登录后进入花之都管理端，可查看用户、商家、订单和系统配置，统一管理后台运营。
        </p>
        <div class="login-hints">
          <span class="brand-pill">演示账号：admin</span>
          <span class="brand-pill">演示密码：admin123456</span>
        </div>
      </div>

      <form class="login-form" @submit.prevent="submitLogin">
        <div v-if="noticeMessage" class="login-notice">{{ noticeMessage }}</div>

        <div class="form-group">
          <label>账号</label>
          <input v-model.trim="account" class="form-control" autocomplete="username" placeholder="请输入管理员账号">
        </div>

        <div class="form-group">
          <label>密码</label>
          <input
            v-model="password"
            class="form-control"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
          >
        </div>

        <label class="remember-line">
          <input v-model="rememberMe" type="checkbox">
          <span>记住我</span>
        </label>

        <div class="login-actions">
          <router-link class="login-link" to="/register">注册账号</router-link>
          <router-link class="login-link" to="/change-password">修改密码</router-link>
        </div>

        <div v-if="errorMessage" class="login-error">{{ errorMessage }}</div>

        <button class="btn btn-primary login-submit" :disabled="loading" type="submit">
          {{ loading ? '登录中…' : '登录管理端' }}
        </button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, saveStoredAuth } from '../../api'

const router = useRouter()
const route = useRoute()

const account = ref('admin')
const password = ref('admin123456')
const rememberMe = ref(false)
const loading = ref(false)
const errorMessage = ref('')

const noticeMessage = computed(() => {
  if (route.query.notice === 'password-updated') return '密码已修改，请使用新密码重新登录。'
  if (route.query.notice === 'registered') return '账号已创建，请登录或继续使用当前会话。'
  return ''
})

async function submitLogin() {
  errorMessage.value = ''
  loading.value = true

  try {
    const res = await api.login(account.value, password.value)
    if (!res.success || !res.data?.token) {
      errorMessage.value = res.message || '登录失败，请检查账号和密码'
      return
    }

    saveStoredAuth(res.data, rememberMe.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : res.data.landingPath || '/'
    router.replace(redirect)
  } catch {
    errorMessage.value = '网络异常，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-shell {
  min-height: 100vh;
  position: relative;
  isolation: isolate;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  background:
    linear-gradient(135deg, rgba(255, 246, 249, 0.96), rgba(247, 251, 248, 0.92)),
    radial-gradient(circle at top left, rgba(234, 147, 173, 0.22), transparent 38%),
    radial-gradient(circle at bottom right, rgba(101, 189, 137, 0.18), transparent 32%),
    var(--bg);
}

.login-shell::before,
.login-shell::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.login-shell::before {
  inset: -12%;
  background:
    radial-gradient(circle at 12% 18%, rgba(255, 160, 196, 0.34), transparent 18%),
    radial-gradient(circle at 84% 22%, rgba(255, 209, 102, 0.2), transparent 20%),
    radial-gradient(circle at 70% 78%, rgba(103, 188, 145, 0.24), transparent 20%),
    radial-gradient(circle at 22% 82%, rgba(255, 255, 255, 0.4), transparent 16%);
  filter: blur(10px);
  transform: scale(1.08);
  z-index: 0;
}

.login-shell::after {
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.26) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.2) 1px, transparent 1px);
  background-size: 34px 34px;
  mask-image: radial-gradient(circle at center, rgba(0, 0, 0, 0.9), transparent 88%);
  opacity: 0.5;
  z-index: 0;
}

.login-panel {
  width: min(920px, 100%);
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 28px;
  padding: 32px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.78), rgba(255, 255, 255, 0.6));
  backdrop-filter: blur(22px);
  box-shadow:
    0 24px 60px rgba(173, 92, 122, 0.14),
    0 10px 20px rgba(57, 86, 68, 0.08);
}

.login-panel::before {
  content: '';
  position: absolute;
  inset: 14px;
  border-radius: 24px;
  border: 1px solid rgba(255, 255, 255, 0.34);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.28), transparent 38%);
  pointer-events: none;
}

.login-kicker {
  margin: 0 0 12px;
  letter-spacing: 0.18em;
  font-size: 12px;
  color: var(--text-muted);
}

.login-title {
  margin: 0;
  font-size: 40px;
  line-height: 1.1;
  text-shadow: 0 8px 24px rgba(172, 89, 127, 0.12);
}

.login-subtitle {
  margin: 18px 0 0;
  color: var(--text-secondary);
  line-height: 1.8;
}

.login-hints {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 24px;
}

.login-form {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 18px;
  padding: 24px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.52);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.5);
}

.login-notice,
.login-error {
  border-radius: 14px;
  padding: 12px 14px;
  font-size: 14px;
}

.login-notice {
  background: rgba(73, 168, 116, 0.12);
  color: #26764a;
}

.login-error {
  background: rgba(229, 85, 110, 0.12);
  color: #b93859;
}

.remember-line {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--text-secondary);
}

.login-actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.login-link {
  color: var(--primary);
  font-size: 14px;
  text-decoration: none;
}

.login-link:hover {
  text-decoration: underline;
}

.login-submit {
  width: 100%;
  justify-content: center;
  box-shadow: 0 16px 30px rgba(197, 102, 135, 0.18);
}

@media (max-width: 860px) {
  .login-panel {
    grid-template-columns: 1fr;
    padding: 24px;
  }

  .login-title {
    font-size: 32px;
  }
}
</style>