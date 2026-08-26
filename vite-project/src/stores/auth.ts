import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api } from '../api'
import type { User } from '../api/mockApi'
import { useDeviceStore } from './device'
import { useWebSocket } from './websocket'

/** 解析 JWT payload 中的 exp（秒级时间戳），无效令牌返回 null */
function tokenExp(token: string): number | null {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    return typeof payload.exp === 'number' ? payload.exp : null
  } catch {
    return null
  }
}

/** 判断本地令牌是否已过期 */
function isTokenExpired(token: string): boolean {
  const exp = tokenExp(token)
  return exp == null || exp * 1000 <= Date.now()
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const token = ref<string | null>(null)
  const loading = ref(false)

  const isLoggedIn = computed(() => !!token.value && !!user.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN' || user.value?.role === 'admin')
  const username = computed(() => user.value?.username || '')

  async function initAuth() {
    const storedToken = localStorage.getItem('iot_token')
    if (storedToken) {
      if (isTokenExpired(storedToken)) {
        localStorage.removeItem('iot_token')
        return
      }
      token.value = storedToken
      try {
        const currentUser = await api.getCurrentUser()
        user.value = currentUser
      } catch {
        token.value = null
        localStorage.removeItem('iot_token')
      }
    }
  }

  async function login(loginUsername: string, password: string) {
    loading.value = true
    try {
      const result = await api.login(loginUsername, password)
      user.value = result.user as User
      token.value = result.token
      return true
    } finally {
      loading.value = false
    }
  }

  async function sendCode(phone: string) {
    return await api.sendVerificationCode(phone)
  }

  async function loginByPhone(phone: string, code: string) {
    loading.value = true
    try {
      const result = await api.loginByPhone(phone, code)
      user.value = result.user as User
      token.value = result.token
      return true
    } finally {
      loading.value = false
    }
  }

  async function register(regUsername: string, email: string, password: string) {
    loading.value = true
    try {
      await api.register(regUsername, email, password)
      return true
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    // 断开实时推送，避免登出后仍持续接收遥测/告警
    useWebSocket().disconnect()
    // 清空本账号留下的全部本地数据（仅保留主题偏好）
    Object.keys(localStorage)
      .filter(key => key.startsWith('iot_') && key !== 'iot_theme')
      .forEach(key => localStorage.removeItem(key))
    user.value = null
    token.value = null
    // Clear device store so next user sees a clean slate
    const deviceStore = useDeviceStore()
    deviceStore.reset()
  }

  return {
    user, token, loading,
    isLoggedIn, isAdmin, username,
    initAuth, login, loginByPhone, register, logout, sendCode
  }
})
