<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useTaskStore } from '@/stores/task'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const taskStore = useTaskStore()

const mode = ref<'login' | 'register'>('login')
const submitting = ref(false)
const form = reactive({
  username: '',
  email: '',
  password: '',
})

async function onSubmit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请填写用户名与密码')
    return
  }
  submitting.value = true
  try {
    if (mode.value === 'login') {
      await auth.login(form.username, form.password)
    } else {
      if (!form.email) {
        ElMessage.warning('请填写邮箱')
        return
      }
      await auth.register(form.username, form.email, form.password)
    }
    // 登录成功后建立 STOMP 连接
    taskStore.connect()
    const redirect = (route.query.redirect as string) || '/projects'
    router.push(redirect)
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-glow"></div>

    <div class="login-card anim-fade-up">
      <div class="brand-block">
        <svg width="34" height="34" viewBox="0 0 32 32" fill="none">
          <path d="M8 22 L16 8 L24 22 Z" stroke="var(--accent)" stroke-width="2.4"
            stroke-linejoin="round" />
        </svg>
        <span class="brand-name">MirageStudio</span>
      </div>
      <p class="brand-sub">{{ mode === 'login' ? '登录进入工作台' : '创建账户开始创作' }}</p>

      <form class="login-form" @submit.prevent="onSubmit">
        <div class="field">
          <label class="field-label">用户名</label>
          <input v-model="form.username" class="input" placeholder="输入用户名" autocomplete="username" />
        </div>

        <div v-if="mode === 'register'" class="field">
          <label class="field-label">邮箱</label>
          <input v-model="form.email" class="input" type="email" placeholder="name@example.com" autocomplete="email" />
        </div>

        <div class="field">
          <label class="field-label">密码</label>
          <input v-model="form.password" class="input" type="password" placeholder="输入密码"
            autocomplete="current-password" />
        </div>

        <button class="btn btn-primary login-submit" :disabled="submitting">
          <span v-if="submitting" class="spinner"></span>
          {{ mode === 'login' ? '登录' : '注册' }}
        </button>
      </form>

      <div class="switch-mode">
        <span>{{ mode === 'login' ? '还没有账户？' : '已有账户？' }}</span>
        <button class="link-btn" @click="mode = mode === 'login' ? 'register' : 'login'">
          {{ mode === 'login' ? '注册' : '登录' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.login-glow {
  position: absolute;
  width: 520px;
  height: 520px;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: radial-gradient(circle, rgba(91, 91, 214, 0.18) 0%, transparent 62%);
  filter: blur(8px);
  pointer-events: none;
}

.login-card {
  width: 360px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 32px 28px;
  position: relative;
  z-index: 1;
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 10px;
}
.brand-name {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: -0.01em;
}
.brand-sub {
  margin-top: 6px;
  margin-bottom: 26px;
  color: var(--text-dim);
  font-size: 12.5px;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.login-submit {
  width: 100%;
  height: 36px;
  margin-top: 4px;
}

.switch-mode {
  margin-top: 22px;
  text-align: center;
  font-size: 12px;
  color: var(--text-dim);
}
.link-btn {
  color: var(--accent);
  font-weight: 500;
  margin-left: 4px;
}
.link-btn:hover {
  text-decoration: underline;
}
</style>
