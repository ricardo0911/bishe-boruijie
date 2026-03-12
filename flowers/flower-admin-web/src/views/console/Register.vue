<template>
  <div class="login-shell">
    <div class="login-panel card single-panel">
      <div class="login-copy compact-copy">
        <p class="login-kicker">FLOWER CAPITAL CONSOLE</p>
        <h1 class="login-title">注册管理员账号</h1>
        <p class="login-subtitle">创建后自动登录到管理端，也可以返回登录页使用新账号登录。</p>
      </div>

      <form class="login-form" @submit.prevent="submitRegister">
        <div class="form-group">
          <label>显示名称</label>
          <input v-model.trim="displayName" class="form-control" placeholder="请输入显示名称">
        </div>

        <div class="form-group">
          <label>账号</label>
          <input v-model.trim="account" class="form-control" autocomplete="username" placeholder="请输入账号">
        </div>

        <div class="form-group">
          <label>密码</label>
          <input v-model="password" class="form-control" type="password" autocomplete="new-password" placeholder="至少 8 位">
        </div>

        <div class="form-group">
          <label>确认密码</label>
          <input v-model="confirmPassword" class="form-control" type="password" autocomplete="new-password" placeholder="请再次输入密码">
        </div>

        <label class="remember-line">
          <input v-model="rememberMe" type="checkbox">
          <span>注册后记住我</span>
        </label>

        <div class="login-actions">
          <router-link class="login-link" to="/login">返回登录</router-link>
          <router-link class="login-link" to="/change-password">修改密码</router-link>
        </div>

        <div v-if="errorMessage" class="login-error">{{ errorMessage }}</div>

        <button class="btn btn-primary login-submit" :disabled="loading" type="submit">
          {{ loading ? '提交中…' : '注册并进入管理端' }}
        </button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, saveStoredAuth } from '../../api'

const router = useRouter()
const displayName = ref('')
const account = ref('')
const password = ref('')
const confirmPassword = ref('')
const rememberMe = ref(false)
const loading = ref(false)
const errorMessage = ref('')

async function submitRegister() {
  errorMessage.value = ''

  if (!account.value || !password.value || !confirmPassword.value) {
    errorMessage.value = '请完整填写注册信息'
    return
  }
  if (password.value.length < 8) {
    errorMessage.value = '密码至少需要 8 位'
    return
  }
  if (password.value !== confirmPassword.value) {
    errorMessage.value = '两次输入的密码不一致'
    return
  }

  loading.value = true
  try {
    const res = await api.register(account.value, password.value, displayName.value)
    if (!res.success || !res.data?.token) {
      errorMessage.value = res.message || '注册失败，请稍后重试'
      return
    }

    saveStoredAuth(res.data, rememberMe.value)
    router.replace(res.data.landingPath || '/')
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
  background: radial-gradient(circle at 12% 18%, rgba(255, 160, 196, 0.34), transparent 18%), radial-gradient(circle at 84% 22%, rgba(255, 209, 102, 0.2), transparent 20%), radial-gradient(circle at 70% 78%, rgba(103, 188, 145, 0.24), transparent 20%), radial-gradient(circle at 22% 82%, rgba(255, 255, 255, 0.4), transparent 16%);
  filter: blur(10px);
  transform: scale(1.08);
  z-index: 0;
}
.login-shell::after {
  background-image: linear-gradient(rgba(255, 255, 255, 0.26) 1px, transparent 1px), linear-gradient(90deg, rgba(255, 255, 255, 0.2) 1px, transparent 1px);
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
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.78), rgba(255, 255, 255, 0.6));
  backdrop-filter: blur(22px);
  box-shadow: 0 24px 60px rgba(173, 92, 122, 0.14), 0 10px 20px rgba(57, 86, 68, 0.08);
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
.single-panel {
  width: min(640px, 100%);
  grid-template-columns: 1fr;
}
.compact-copy { margin-bottom: 4px; }
.login-kicker { margin: 0 0 12px; letter-spacing: 0.18em; font-size: 12px; color: var(--text-muted); }
.login-title { margin: 0; font-size: 40px; line-height: 1.1; text-shadow: 0 8px 24px rgba(172, 89, 127, 0.12); }
.login-subtitle { margin: 18px 0 0; color: var(--text-secondary); line-height: 1.8; }
.login-form { display: flex; flex-direction: column; justify-content: center; gap: 18px; padding: 24px; border-radius: 24px; background: rgba(255, 255, 255, 0.52); box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.5); }
.login-error { border-radius: 14px; padding: 12px 14px; font-size: 14px; background: rgba(229, 85, 110, 0.12); color: #b93859; }
.remember-line { display: inline-flex; align-items: center; gap: 10px; color: var(--text-secondary); }
.login-actions { display: flex; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.login-link { color: var(--primary); font-size: 14px; text-decoration: none; }
.login-link:hover { text-decoration: underline; }
.login-submit { width: 100%; justify-content: center; box-shadow: 0 16px 30px rgba(197, 102, 135, 0.18); }
</style>