import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User } from '@/types'
import * as authApi from '@/api/auth'
import { tokenStorage, userStorage } from '@/api/client'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const user = ref<User | null>(null)
  const loading = ref(false)

  const isAuthenticated = computed(() => !!token.value)

  /** 从 localStorage 恢复会话 */
  function restore() {
    const t = tokenStorage.get()
    const u = userStorage.get()
    if (t) token.value = t
    if (u) {
      try {
        user.value = JSON.parse(u)
      } catch {
        userStorage.clear()
      }
    }
  }

  function setSession(t: string, u: User) {
    token.value = t
    user.value = u
    tokenStorage.set(t)
    userStorage.set(JSON.stringify(u))
  }

  async function login(username: string, password: string) {
    loading.value = true
    try {
      const res = await authApi.login({ username, password })
      setSession(res.token, res.user)
      return res
    } finally {
      loading.value = false
    }
  }

  async function register(username: string, email: string, password: string) {
    loading.value = true
    try {
      const res = await authApi.register({ username, email, password })
      setSession(res.token, res.user)
      return res
    } finally {
      loading.value = false
    }
  }

  function logout() {
    token.value = null
    user.value = null
    tokenStorage.clear()
    userStorage.clear()
  }

  return { token, user, loading, isAuthenticated, restore, login, register, logout }
})
