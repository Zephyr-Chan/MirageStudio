/* ============================================================
   MirageStudio 共享类型
   ============================================================ */

export interface User {
  id: number
  username: string
  email: string
  role?: string
}

/* ---- 项目 ---- */
export type ProjectStatus = 'DRAFT' | 'RECONSTRUCTING' | 'READY' | 'EFFECT_APPLYING' | 'ARCHIVED'

export interface Project {
  id: number
  name: string
  description?: string
  status: ProjectStatus
  photoCount: number
  thumbnailUrl?: string
  splatUrl?: string
  createdAt: string
  updatedAt: string
}

export interface ProjectCreate {
  name: string
  description?: string
}

/* ---- 重建参数 ---- */
export interface ReconstructionParams {
  quality: 'draft' | 'standard' | 'high'
  featureMatching: boolean
  denseReconstruction: boolean
  resolutionScale: number
  iterations: number
}

/* ---- 特效 ---- */
export interface EffectTemplate {
  id: string
  name: string
  description: string
  prompt: string
  previewUrl?: string
}

export interface EffectParams {
  templateId: string
  prompt: string
  seed: number
  controlnetStrength: number
  denoiseStrength: number
}

/* ---- 任务 ---- */
export type TaskType = 'RECONSTRUCTION' | 'EFFECT' | 'EXPORT'
export type TaskStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELLED'

export interface Task {
  id: number
  projectId: number
  projectName?: string
  type: TaskType
  status: TaskStatus
  progress: number
  message?: string
  log?: string[]
  createdAt: string
  updatedAt: string
}

/** STOMP 任务状态推送报文 */
export interface TaskStatusMessage {
  taskId: number
  projectId: number
  status: TaskStatus
  progress: number
  message?: string
  logLine?: string
}

/* ---- Agent ---- */
export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  createdAt: number
}

export interface ReActStep {
  id: string
  type: 'thought' | 'action' | 'observation'
  content: string
  tool?: string
  status: 'pending' | 'running' | 'done'
  createdAt: number
}
