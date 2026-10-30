<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { login as apiLogin, register as apiRegister } from '../api/auth'
import { setAuth } from '../store/auth'

const mode = ref('login')
const loading = ref(false)
const form = ref({ username: '', password: '', nickname: '' })

const isLogin = computed(() => mode.value === 'login')
// 部署后前端在 nginx 上（8088），开发时在 vite（5173），所以不能写死后端端口
const host = window.location.host
const title = computed(() => (isLogin.value ? '登录' : '创建账号'))
const hint = computed(() =>
  isLogin.value ? '登录后可管理你的短链与访问统计' : '用户名 3-20 位字母、数字或下划线；密码至少 6 位'
)

function switchMode() {
  mode.value = isLogin.value ? 'register' : 'login'
  form.value.password = ''
}

async function submit() {
  const username = form.value.username.trim()
  const password = form.value.password
  if (!username || !password) {
    ElMessage.warning('用户名和密码都要填')
    return
  }
  if (!isLogin.value && password.length < 6) {
    ElMessage.warning('密码至少 6 位')
    return
  }
  loading.value = true
  try {
    const data = isLogin.value
      ? await apiLogin({ username, password })
      : await apiRegister({ username, password, nickname: form.value.nickname.trim() || undefined })
    setAuth(data.token, data.user)
    ElMessage.success(isLogin.value ? '登录成功' : '注册成功，已自动登录')
  } catch (e) {
    /* 拦截器已经弹过错误消息 */
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-wrap">
    <div class="auth-glow" />

    <form class="auth-card" @submit.prevent="submit">
      <div class="auth-brand">
        <span class="mark" />
        <span class="wordmark">Shortlink</span>
        <span class="badge">v5</span>
      </div>

      <div class="eyebrow" style="margin-top: 26px">Sign in</div>
      <h1 class="auth-title">{{ title }}</h1>
      <p class="auth-sub">{{ hint }}</p>

      <label class="field">
        <span class="cap">用户名</span>
        <input
          v-model="form.username"
          class="input"
          autocomplete="username"
          placeholder="请输入用户名"
          @keyup.enter="submit"
        />
      </label>

      <label v-if="!isLogin" class="field">
        <span class="cap">昵称（可不填）</span>
        <input v-model="form.nickname" class="input" placeholder="怎么称呼你" @keyup.enter="submit" />
      </label>

      <label class="field">
        <span class="cap">密码</span>
        <input
          v-model="form.password"
          class="input"
          type="password"
          autocomplete="current-password"
          placeholder="••••••"
          @keyup.enter="submit"
        />
      </label>

      <button class="btn primary block" :disabled="loading" type="submit">
        {{ loading ? '处理中…' : isLogin ? '登录' : '注册并登录' }}
      </button>

      <button class="auth-switch" type="button" @click="switchMode">
        {{ isLogin ? '没有账号？创建一个' : '已经有账号？去登录' }}
      </button>
    </form>

    <p class="auth-foot mono">{{ host }} · JWT + ThreadLocal</p>
  </div>
</template>
