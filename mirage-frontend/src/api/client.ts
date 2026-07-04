import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

const TOKEN_KEY = 'mirage_token'
const USER_KEY = 'mirage_user'

export const tokenStorage = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (t: string) => localStorage.setItem(TOKEN_KEY, t),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

export const userStorage = {
  get: () => localStorage.getItem(USER_KEY),
  set: (u: string) => localStorage.setItem(USER_KEY, u),
  clear: () => localStorage.removeItem(USER_KEY),
}

const client: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 30000,
})

// 请求拦截器：注入 JWT
client.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = tokenStorage.get()
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：解包 R<T> 统一响应 + 错误处理
// 后端 R<T> 格式: { code: 0, message: "...", data: T, success: true }
// 拦截后 response.data 直接就是 T (即 R.data)
client.interceptors.response.use(
  (resp) => {
    const body = resp.data
    // 检查是否是 R<T> 封装格式
    if (body && typeof body === 'object' && 'code' in body && 'success' in body) {
      if (body.success === false || body.code !== 0) {
        // 业务失败
        const msg = body.message || '请求失败'
        ElMessage.error(msg)
        return Promise.reject(new Error(msg))
      }
      // 成功：解包 data
      resp.data = body.data
    }
    return resp
  },
  (error) => {
    const status = error.response?.status
    // 后端异常处理器返回 R.fail() 但 HTTP 200；HTTP 4xx/5xx 走这里
    const body = error.response?.data
    const detail = body?.message || body?.detail || error.message

    if (status === 401) {
      tokenStorage.clear()
      userStorage.clear()
      if (!window.location.pathname.includes('/login')) {
        ElMessage.error('登录已失效，请重新登录')
        setTimeout(() => {
          window.location.href = '/login'
        }, 600)
      }
    } else if (status && status >= 500) {
      ElMessage.error(`服务异常：${detail}`)
    } else if (status && status >= 400) {
      ElMessage.error(detail || '请求失败')
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请检查后端是否已启动')
    } else if (error.code === 'ERR_NETWORK') {
      ElMessage.error('网络连接失败，请检查后端服务是否运行在 localhost:8080')
    }
    return Promise.reject(error)
  }
)

export default client
