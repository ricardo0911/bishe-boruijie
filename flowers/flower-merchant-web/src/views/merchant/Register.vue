<template>
  <div class="login-shell">
    <div class="login-panel card single-panel">
      <div class="login-copy compact-copy">
        <p class="login-kicker">HUAZHIDU MERCHANT STUDIO</p>
        <h1 class="login-title">注册商家账号</h1>
        <p class="login-subtitle">创建后自动进入商家端，方便直接开始配置商品和订单。</p>
      </div>
      <form class="login-form" @submit.prevent="submitRegister">
        <div class="form-group"><label>显示名称</label><input v-model.trim="displayName" class="form-control" placeholder="请输入显示名称"></div>
        <div class="form-group"><label>账号</label><input v-model.trim="account" class="form-control" autocomplete="username" placeholder="请输入商家账号"></div>
        <div class="form-group"><label>密码</label><input v-model="password" class="form-control" type="password" autocomplete="new-password" placeholder="至少 8 位"></div>
        <div class="form-group"><label>确认密码</label><input v-model="confirmPassword" class="form-control" type="password" autocomplete="new-password" placeholder="请再次输入密码"></div>
        <label class="remember-line"><input v-model="rememberMe" type="checkbox"><span>注册后记住我</span></label>
        <div class="login-actions"><router-link class="login-link" to="/merchant/login">返回登录</router-link><router-link class="login-link" to="/merchant/change-password">修改密码</router-link></div>
        <div v-if="errorMessage" class="login-error">{{ errorMessage }}</div>
        <button class="btn btn-primary login-submit" :disabled="loading" type="submit">{{ loading ? '提交中…' : '注册并进入商家端' }}</button>
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
  if (!account.value || !password.value || !confirmPassword.value) return (errorMessage.value = '请完整填写注册信息')
  if (password.value.length < 8) return (errorMessage.value = '密码至少需要 8 位')
  if (password.value !== confirmPassword.value) return (errorMessage.value = '两次输入的密码不一致')
  loading.value = true
  try {
    const res = await api.register(account.value, password.value, displayName.value)
    if (!res.success || !res.data?.token) return (errorMessage.value = res.message || '注册失败，请稍后重试')
    saveStoredAuth(res.data, rememberMe.value)
    router.replace(res.data.landingPath || '/merchant')
  } catch {
    errorMessage.value = '网络异常，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-shell { min-height: 100vh; position: relative; isolation: isolate; overflow: hidden; display: flex; align-items: center; justify-content: center; padding: 32px 16px; background: linear-gradient(135deg, rgba(246, 252, 248, 0.96), rgba(255, 247, 250, 0.92)), radial-gradient(circle at top left, rgba(213, 111, 141, 0.18), transparent 36%), radial-gradient(circle at bottom right, rgba(61, 147, 100, 0.16), transparent 30%), var(--bg); }
.login-shell::before,.login-shell::after { content: ''; position: absolute; inset: 0; pointer-events: none; }
.login-shell::before { inset: -10%; background: radial-gradient(circle at 10% 16%, rgba(93, 173, 129, 0.26), transparent 17%), radial-gradient(circle at 85% 18%, rgba(248, 155, 186, 0.24), transparent 18%), radial-gradient(circle at 76% 80%, rgba(252, 221, 122, 0.18), transparent 18%), radial-gradient(circle at 26% 78%, rgba(255, 255, 255, 0.38), transparent 15%); filter: blur(12px); transform: scale(1.06); z-index: 0; }
.login-shell::after { background-image: linear-gradient(rgba(255, 255, 255, 0.24) 1px, transparent 1px), linear-gradient(90deg, rgba(255, 255, 255, 0.18) 1px, transparent 1px); background-size: 34px 34px; mask-image: radial-gradient(circle at center, rgba(0, 0, 0, 0.9), transparent 88%); opacity: 0.48; z-index: 0; }
.login-panel { width: min(920px, 100%); position: relative; z-index: 1; display: grid; grid-template-columns: 1.1fr 0.9fr; gap: 28px; padding: 32px; border: 1px solid rgba(255, 255, 255, 0.72); background: linear-gradient(145deg, rgba(255, 255, 255, 0.76), rgba(255, 255, 255, 0.58)); backdrop-filter: blur(22px); box-shadow: 0 24px 60px rgba(73, 125, 96, 0.16), 0 10px 22px rgba(171, 88, 120, 0.08); }
.login-panel::before { content: ''; position: absolute; inset: 14px; border-radius: 24px; border: 1px solid rgba(255, 255, 255, 0.34); background: linear-gradient(180deg, rgba(255, 255, 255, 0.28), transparent 38%); pointer-events: none; }
.single-panel { width: min(640px, 100%); grid-template-columns: 1fr; }
.compact-copy { margin-bottom: 4px; }
.login-kicker { margin: 0 0 12px; letter-spacing: 0.18em; font-size: 12px; color: var(--text-muted); }
.login-title { margin: 0; font-size: 40px; line-height: 1.1; text-shadow: 0 8px 24px rgba(74, 132, 94, 0.12); }
.login-subtitle { margin: 18px 0 0; color: var(--text-secondary); line-height: 1.8; }
.login-form { display: flex; flex-direction: column; justify-content: center; gap: 18px; padding: 24px; border-radius: 24px; background: rgba(255, 255, 255, 0.5); box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.5); }
.login-error { border-radius: 14px; padding: 12px 14px; font-size: 14px; background: rgba(229, 85, 110, 0.12); color: #b93859; }
.remember-line { display: inline-flex; align-items: center; gap: 10px; color: var(--text-secondary); }
.login-actions { display: flex; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.login-link { color: var(--primary); font-size: 14px; text-decoration: none; }
.login-link:hover { text-decoration: underline; }
.login-submit { width: 100%; justify-content: center; box-shadow: 0 16px 30px rgba(80, 145, 103, 0.18); }
</style>