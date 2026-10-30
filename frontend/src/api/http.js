import axios from 'axios'
import { ElMessage } from 'element-plus'
import { auth, clearAuth } from '../store/auth'

/**
 * 后端所有接口都返回 { code, message, data }。
 * 这里统一拆包：
 *   - code === 200  → 直接把 data 交给业务代码
 *   - 其它           → 弹消息 + 抛错，业务代码只管 try/catch
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 每个请求自动带 token，业务代码不用管
http.interceptors.request.use((config) => {
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 200) return body.data
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (err) => {
    const status = err.response?.status
    const body = err.response?.data
    const msg = body?.message || err.message || '网络异常'

    // 401 = token 过期或被拉黑。登录接口自己返回的 401（密码错）不能触发跳转，
    // 否则用户在登录页输错密码会被"踢"到登录页，看起来像页面在闪。
    const isAuthApi = (err.config?.url || '').includes('/auth/login') ||
      (err.config?.url || '').includes('/auth/register')
    if (status === 401 && !isAuthApi) {
      clearAuth()
      ElMessage.error('登录已失效，请重新登录')
      location.hash = '#/login'
      return Promise.reject(err)
    }

    ElMessage.error(msg)
    return Promise.reject(err)
  }
)

export default http
