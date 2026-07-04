import client from './client'
import type { EffectParams, EffectTemplate, Task } from '@/types'

interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

/**
 * 获取特效模板列表 (WorkflowController /api/workflows)
 */
export async function listTemplates(): Promise<EffectTemplate[]> {
  const { data } = await client.get<PageResult<any>>('/workflows', {
    params: { current: 1, size: 100 },
  })
  // 如果后端返回的是 Page 而非 List，取 records
  const records = Array.isArray(data) ? data : (data as PageResult<any>).records
  return (records ?? []).map((w: any) => ({
    id: String(w.id),
    name: w.name,
    description: w.category ?? '',
    prompt: '',
    previewUrl: w.thumbnailUrl,
  }))
}

/**
 * 提交特效生成任务
 * 后端: POST /api/effects, body: { projectId, sourceSnapshotAssetId, templateId, paramsJson }
 */
export async function applyEffect(
  projectId: number | string,
  params: EffectParams
): Promise<{ taskId: number; status: string }> {
  const { data } = await client.post('/effects', {
    projectId: Number(projectId),
    templateId: params.templateId ? Number(params.templateId) : undefined,
    paramsJson: JSON.stringify({
      prompt: params.prompt,
      seed: params.seed,
      controlnetStrength: params.controlnetStrength,
      denoiseStrength: params.denoiseStrength,
    }),
  })
  return data
}

/**
 * 获取项目下的特效任务列表
 */
export async function listEffectTasks(
  projectId: number | string
): Promise<Task[]> {
  const { data } = await client.get<PageResult<any>>('/effects', {
    params: { projectId, current: 1, size: 50 },
  })
  return data.records.map((t: any) => ({
    id: t.id,
    projectId: t.projectId,
    type: 'EFFECT' as const,
    status: t.status,
    progress: t.progress ?? 0,
    message: t.errorMsg,
    createdAt: t.createdAt,
    updatedAt: t.updatedAt ?? t.createdAt,
  }))
}

/**
 * 获取单个特效任务
 */
export async function getEffectTask(id: number | string): Promise<Task> {
  const { data } = await client.get(`/effects/${id}`)
  return {
    id: data.id,
    projectId: data.projectId,
    type: 'EFFECT' as const,
    status: data.status,
    progress: data.progress ?? 0,
    message: data.errorMsg,
    createdAt: data.createdAt,
    updatedAt: data.updatedAt ?? data.createdAt,
  }
}

/**
 * 上传截屏（来自 3D 查看器）作为特效输入参考
 * 通过 AssetController /api/assets/upload, type=SCENE_SNAPSHOT
 */
export async function uploadScreenshot(
  projectId: number | string,
  blob: Blob,
  name = 'screenshot.png'
): Promise<{ id: number; storageKey: string; storageBucket: string }> {
  const formData = new FormData()
  formData.append('file', blob, name)
  formData.append('projectId', String(projectId))
  formData.append('type', 'SCENE_SNAPSHOT')

  const { data } = await client.post('/assets/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return data
}

/**
 * 取消特效任务
 */
export async function cancelEffect(id: number | string): Promise<void> {
  await client.post(`/effects/${id}/cancel`)
}
