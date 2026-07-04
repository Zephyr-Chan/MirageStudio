<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const isLogin = computed(() => route.name === 'login')

const navItems = [
  { name: 'projects', label: '项目', to: '/projects' },
  { name: 'tasks', label: '任务', to: '/tasks' },
]

function onLogout() {
  auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="app-shell">
    <!-- 登录页无导航 -->
    <template v-if="isLogin">
      <router-view />
    </template>

    <template v-else>
      <header class="app-header">
        <div class="header-left">
          <router-link to="/projects" class="brand">
            <svg width="20" height="20" viewBox="0 0 32 32" fill="none">
              <path d="M8 22 L16 8 L24 22 Z" stroke="var(--accent)" stroke-width="2.4"
                stroke-linejoin="round" />
            </svg>
            <span>MirageStudio</span>
          </router-link>
        </div>

        <nav class="header-nav">
          <router-link
            v-for="item in navItems"
            :key="item.name"
            :to="item.to"
            class="nav-link"
            :class="{ active: route.name === item.name || route.path.startsWith(item.to + '/') }"
          >
            {{ item.label }}
          </router-link>
        </nav>

        <div class="header-right">
          <span class="user-chip">{{ auth.user?.username ?? '未登录' }}</span>
          <button class="btn-subtle btn-text" @click="onLogout">退出</button>
        </div>
      </header>

      <main class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </template>
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.app-header {
  height: var(--header-h);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-bottom: 1px solid var(--border);
  background: var(--bg);
  position: relative;
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  width: 200px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 14px;
  letter-spacing: -0.01em;
}

.header-nav {
  display: flex;
  align-items: center;
  gap: 2px;
}

.nav-link {
  padding: 6px 12px;
  border-radius: var(--radius-sm);
  font-size: 12.5px;
  font-weight: 500;
  color: var(--text-dim);
  transition: background 0.15s var(--ease), color 0.15s var(--ease);
}
.nav-link:hover {
  color: var(--text);
  background: var(--surface-2);
}
.nav-link.active {
  color: var(--text);
  background: var(--surface-2);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 200px;
  justify-content: flex-end;
}

.user-chip {
  font-size: 12px;
  color: var(--text-dim);
  font-family: var(--font-mono);
}

.btn-text {
  height: 26px;
  padding: 0 8px;
  font-size: 12px;
}

.app-main {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

/* 页面切换过渡 */
.page-enter-active,
.page-leave-active {
  transition: opacity 0.22s var(--ease);
}
.page-enter-from {
  opacity: 0;
}
.page-leave-to {
  opacity: 0;
}
</style>
