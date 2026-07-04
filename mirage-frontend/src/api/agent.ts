import client from './client'
import type { ChatMessage, ReActStep } from '@/types'

const BASE = '/agent'

/** 向 Agent 发送自然语言目标，返回任务 id 供 WebSocket 追踪轨迹 */
export async function sendGoal(
  projectId: number | string,
  message: string,
  history?: Pick<ChatMessage, 'role' | 'content'>[]
): Promise<{ taskId: string; reply: ChatMessage }> {
  const { data } = await client.post(`${BASE}/projects/${projectId}/goal`, {
    message,
    history: history ?? [],
  })
  return data
}

/** 获取某次 Agent 任务的 ReAct 轨迹 */
export async function getTrace(taskId: string): Promise<ReActStep[]> {
  const { data } = await client.get<ReActStep[]>(`${BASE}/trace/${taskId}`)
  return data
}
