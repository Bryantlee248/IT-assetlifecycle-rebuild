import axios, {
  AxiosError,
  type AxiosInstance,
  type InternalAxiosRequestConfig
} from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiEnvelope, TokenResponse } from '../types'

const TOKEN_KEY = 'itam_access_token'
const REFRESH_KEY = 'itam_refresh_token'

const baseURL = import.meta.env.VITE_API_BASE || '/api'

const instance: AxiosInstance = axios.create({
  baseURL,
  timeout: 15000
})

// 标记一次请求是否已经过刷新重试，避免刷新成功后仍 401 导致的死循环
interface RetryableConfig extends InternalAxiosRequestConfig {
  __retried?: boolean
}

// ===== 令牌读写（与 Pinia store 解耦，直接操作 localStorage）=====
export function getAccessToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setTokens(access: string, refresh: string): void {
  localStorage.setItem(TOKEN_KEY, access)
  localStorage.setItem(REFRESH_KEY, refresh)
}

export function clearTokens(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
}

// ===== 请求拦截：附加 Bearer =====
instance.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// ===== 刷新令牌的并发队列 =====
let refreshing = false
let pending: Array<(token: string | null) => void> = []

async function doRefresh(): Promise<string | null> {
  const refreshToken = localStorage.getItem(REFRESH_KEY)
  if (!refreshToken) return null
  try {
    const resp = await axios.post<ApiEnvelope<TokenResponse>>(
      `${baseURL}/v1/auth/refresh`,
      { refreshToken }
    )
    if (resp.data.code === 0) {
      setTokens(resp.data.data.accessToken, resp.data.data.refreshToken)
      return resp.data.data.accessToken
    }
    return null
  } catch {
    return null
  }
}

function redirectLogin(): void {
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

// ===== 响应拦截：信封解包 + 401 续期 =====
instance.interceptors.response.use(
  (response) => {
    const env = response.data as ApiEnvelope<unknown>
    // 业务码非 0：提示并 rejection
    if (env && typeof env.code === 'number' && env.code !== 0) {
      ElMessage.error(env.message || '请求失败')
      return Promise.reject(new Error(env.message || `code ${env.code}`))
    }
    return response
  },
  async (error: AxiosError) => {
    const config = error.config as RetryableConfig | undefined
    const status = error.response?.status

    // 401：尝试用 refreshToken 续期并重试一次（refresh 自身失败则直接登出）
    if (status === 401 && config && !config.url?.includes('/v1/auth/refresh')) {
      if (config.__retried) {
        clearTokens()
        redirectLogin()
        return Promise.reject(error)
      }
      if (refreshing) {
        const token = await new Promise<string | null>((resolve) =>
          pending.push(resolve)
        )
        if (token && config) {
          config.headers.Authorization = `Bearer ${token}`
          return instance.request(config)
        }
        return Promise.reject(error)
      }

      refreshing = true
      const newToken = await doRefresh()
      refreshing = false
      const waiters = pending
      pending = []
      waiters.forEach((cb) => cb(newToken))

      if (newToken && config) {
        config.__retried = true
        config.headers.Authorization = `Bearer ${newToken}`
        return instance.request(config)
      }

      clearTokens()
      redirectLogin()
      return Promise.reject(error)
    }

    // 其它错误：展示后端 message
    const env = error.response?.data as ApiEnvelope<unknown> | undefined
    if (env && env.message) {
      ElMessage.error(env.message)
    } else {
      ElMessage.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

// ===== 统一封装：返回 data，自动解包信封 =====
export function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return instance
    .get<ApiEnvelope<T>>(url, { params })
    .then((r) => r.data.data as T)
}

export function post<T>(url: string, data?: unknown): Promise<T> {
  return instance
    .post<ApiEnvelope<T>>(url, data)
    .then((r) => r.data.data as T)
}

export function put<T>(url: string, data?: unknown): Promise<T> {
  return instance
    .put<ApiEnvelope<T>>(url, data)
    .then((r) => r.data.data as T)
}

export function del<T>(url: string): Promise<T> {
  return instance
    .delete<ApiEnvelope<T>>(url)
    .then((r) => r.data.data as T)
}

export function patch<T>(url: string, data?: unknown): Promise<T> {
  return instance
    .patch<ApiEnvelope<T>>(url, data)
    .then((r) => r.data.data as T)
}

export default instance
