import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      redirect: '/projects',
    },
    {
      path: '/projects',
      name: 'projects',
      component: () => import('@/views/ProjectsView.vue'),
    },
    {
      path: '/projects/:id/reconstruct',
      name: 'reconstruct',
      component: () => import('@/views/ReconstructView.vue'),
      props: true,
    },
    {
      path: '/projects/:id/studio',
      name: 'studio',
      component: () => import('@/views/StudioView.vue'),
      props: true,
    },
    {
      path: '/tasks',
      name: 'tasks',
      component: () => import('@/views/TasksView.vue'),
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/projects',
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  // 首次进入尝试从 localStorage 恢复
  if (!auth.token) auth.restore()

  if (!to.meta.public && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.isAuthenticated) {
    return { name: 'projects' }
  }
})

export default router
