import { reactive } from 'vue'

const TOKEN_KEY = 'shortlink_token'
const USER_KEY = 'shortlink_user'

/**
 * 一个够用就行的全局登录态。
 * 没用 Pinia：就两个字段（token、user），reactive 已经够了，多引一个库不值得。
 */
export const auth = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  user: safeParse(localStorage.getItem(USER_KEY))
})

export function setAuth(token, user) {
  auth.token = token || ''
  auth.user = user || null
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
  if (user) localStorage.setItem(USER_KEY, JSON.stringify(user))
  else localStorage.removeItem(USER_KEY)
}

export function setUser(user) {
  auth.user = user
  if (user) localStorage.setItem(USER_KEY, JSON.stringify(user))
  else localStorage.removeItem(USER_KEY)
}

export function clearAuth() {
  setAuth('', null)
}

function safeParse(raw) {
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch (e) {
    return null
  }
}
