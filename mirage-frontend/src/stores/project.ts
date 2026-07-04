import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Project, ProjectCreate } from '@/types'
import * as projectApi from '@/api/project'

export const useProjectStore = defineStore('project', () => {
  const projects = ref<Project[]>([])
  const current = ref<Project | null>(null)
  const loading = ref(false)

  async function fetchAll() {
    loading.value = true
    try {
      projects.value = await projectApi.listProjects()
    } finally {
      loading.value = false
    }
  }

  async function fetchOne(id: number | string) {
    loading.value = true
    try {
      current.value = await projectApi.getProject(id)
      return current.value
    } finally {
      loading.value = false
    }
  }

  async function create(payload: ProjectCreate) {
    const p = await projectApi.createProject(payload)
    projects.value.unshift(p)
    return p
  }

  async function remove(id: number | string) {
    await projectApi.deleteProject(id)
    projects.value = projects.value.filter((p) => p.id !== id)
  }

  /** 就地更新某个项目（用于 STOMP 状态推送后同步） */
  function patch(id: number, patch: Partial<Project>) {
    const idx = projects.value.findIndex((p) => p.id === id)
    if (idx >= 0) {
      projects.value[idx] = { ...projects.value[idx], ...patch }
    }
    if (current.value?.id === id) {
      current.value = { ...current.value, ...patch }
    }
  }

  return { projects, current, loading, fetchAll, fetchOne, create, remove, patch }
})
