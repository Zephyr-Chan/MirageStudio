import client from './client'
import type { Task, TaskType } from '@/types'
import { listReconstructionTasks } from './reconstruction'
import { listEffectTasks } from './effect'

/**
 * 列出所有任务（合并重建+特效任务）
 * 后端没有统一的 /api/tasks 列表端点，分别从 /recon 和 /effects 获取
 */
export async function listTasks(params?: {
  type?: TaskType
  status?: string
  projectId?: number
}): Promise<Task[]> {
  const projectId = params?.projectId
  const reconPromise = projectId
    ? listReconstructionTasks(projectId)
    : Promise.resolve([])
  const effectPromise = projectId
    ? listEffectTasks(projectId)
    : Promise.resolve([])

  const [reconTasks, effectTasks] = await Promise.all([reconPromise, effectPromise])
  let all = [...reconTasks, ...effectTasks]

  if (params?.type) {
    all = all.filter((t) => t.type === params.type)
  }
  if (params?.status) {
    all = all.filter((t) => t.status === params.status)
  }

  // 按创建时间倒序
  all.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
  return all
}

/**
 * 查询任务实时状态 (从 Redis 读取)
 * 后端: GET /api/tasks/{id}
 */
export async function getTask(id: number | string): Promise<Task> {
  const { data } = await client.get(`/tasks/${id}`)
  return {
    id: Number(data.taskId ?? id),
    projectId: data.projectId ?? 0,
    type: data.taskType ?? 'RECONSTRUCTION',
    status: data.status,
    progress: data.progress ?? 0,
    message: data.message,
    createdAt: data.createdAt ?? new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  }
}

/**
 * 取消任务（根据类型路由到对应端点）
 */
export async function cancelTask(id: number | string, type?: TaskType): Promise<void> {
  if (type === 'EFFECT') {
    await client.post(`/effects/${id}/cancel`)
  } else {
    await client.post(`/recon/${id}/cancel`)
  }
}
