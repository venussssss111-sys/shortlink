<script setup>
import { onMounted, ref, watch } from 'vue'
import { me } from './api/auth'
import { auth, setUser } from './store/auth'
import LoginPage from './views/LoginPage.vue'
import UserMenu from './components/UserMenu.vue'
import LinkList from './views/LinkList.vue'
import StatsDashboard from './views/StatsDashboard.vue'

const tab = ref(location.hash === '#stats' ? 'stats' : 'links')

const tabs = [
  { key: 'links', label: '链接管理' },
  { key: 'stats', label: '访问统计' }
]

// 不引 vue-router，用 hash 做最小路由：刷新 / 分享链接也能落到同一页
function go(key) {
  tab.value = key
  location.hash = key === 'stats' ? '#stats' : '#links'
}

/**
 * 刷新页面后本地只剩 token，用它换一次最新的用户信息：
 * token 过期会被拦截器打回登录页，没过期还能顺手更新昵称和头像。
 */
async function syncUser() {
  if (!auth.token) return
  try {
    setUser(await me())
  } catch (e) {
    /* 401 已由拦截器处理 */
  }
}

watch(() => auth.token, (t) => t && syncUser())
onMounted(syncUser)
</script>

<template>
  <LoginPage v-if="!auth.token" />

  <div v-else class="shell">
    <header class="topnav">
      <div class="brand">
        <span class="mark" />
        <span class="wordmark">Shortlink</span>
        <span class="badge">v5</span>
      </div>

      <nav class="nav">
        <button
          v-for="t in tabs"
          :key="t.key"
          :class="['navitem', { on: tab === t.key }]"
          @click="go(t.key)"
        >
          {{ t.label }}
        </button>
      </nav>

      <div class="meta mono">localhost:8080</div>
      <UserMenu />
    </header>

    <main class="main">
      <LinkList v-if="tab === 'links'" />
      <StatsDashboard v-else />
    </main>
  </div>
</template>
