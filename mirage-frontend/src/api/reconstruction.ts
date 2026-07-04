import client from './client'
import type { Task } from '@/types'

/** MyBatis-Plus Page<T> */
interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

/**
 * 上传照片到 MinIO (通过 AssetController /api/assets/upload)
 * 返回创建的资产记录
 */
export async function uploadPhoto(
  projectId: number | string,
  file: File,
  onProgress?: (percent: number) => void
): Promise<{ id: number; type: string; storageKey: string }> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('projectId', String(projectId))
  formData.append('type', 'PHOTO')

  const { data } = await client.post('/assets/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (e) => {
      if (onProgress && e.total) {
        onProgress(Math.round((e.loaded / e.total) * 100))
      }
    },
  })
  return data
}

/**
 * 列出项目下的所有照片资产
 */
export async function listPhotos(
  projectId: number | string
): Promise<Array<{ id: number; storageKey: string; storageBucket: string }>> {
  const { data } = await client.get<PageResult<any>>('/assets', {
    params: { projectId, type: 'PHOTO', current: 1, size: 200 },
  })
  return data.records
}

/**
 * 提交 3DGS 重建任务
 * 后端: POST /api/recon, body: { projectId, sourceAssetIds: [Long], paramsJson: String }
 */
export async function startReconstruction(
  projectId: number | string,
  sourceAssetIds: number[],
  params?: { iterations?: number; resolution?: string }
): Promise<{ taskId: number; status: string }> {
  const { data } = await client.post('/recon', {
    projectId: Number(projectId),
    sourceAssetIds,
    paramsJson: JSON.stringify(params ?? {}),
  })
  return data
}

/**
 * 获取项目下的重建任务列表
 */
export async function listReconstructionTasks(
  projectId: number | string
): Promise<Task[]> {
  const { data } = await client.get<PageResult<any>>('/recon', {
    params: { projectId, current: 1, size: 50 },
  })
  return data.records.map((t: any) => ({
    id: t.id,
    projectId: t.projectId,
    type: 'RECONSTRUCTION' as const,
    status: t.status,
    progress: t.progress ?? 0,
    message: t.errorMsg,
    createdAt: t.createdAt,
    updatedAt: t.updatedAt ?? t.createdAt,
  }))
}

/**
 * 获取单个重建任务
 */
export async function getReconstructionTask(id: number | string): Promise<Task> {
  const { data } = await client.get(`/recon/${id}`)
  return {
    id: data.id,
    projectId: data.projectId,
    type: 'RECONSTRUCTION' as const,
    status: data.status,
    progress: data.progress ?? 0,
    message: data.errorMsg,
    createdAt: data.createdAt,
    updatedAt: data.updatedAt ?? data.createdAt,
  }
}

/**
 * 取消重建任务
 */
export async function cancelReconstruction(id: number | string): Promise<void> {
  await client.post(`/recon/${id}/cancel`)
}
