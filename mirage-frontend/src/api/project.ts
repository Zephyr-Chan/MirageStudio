import client from './client'
import type { Project, ProjectCreate } from '@/types'

/** MyBatis-Plus Page<T> 结构 */
interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

export async function listProjects(): Promise<Project[]> {
  const { data } = await client.get<PageResult<Project>>('/projects')
  return data.records
}

export async function getProject(id: number | string): Promise<Project> {
  const { data } = await client.get<Project>(`/projects/${id}`)
  return data
}

export async function createProject(payload: ProjectCreate): Promise<Project> {
  const { data } = await client.post<Project>('/projects', payload)
  return data
}

export async function updateProject(
  id: number | string,
  payload: Partial<ProjectCreate>
): Promise<Project> {
  const { data } = await client.put<Project>(`/projects/${id}`, payload)
  return data
}

export async function deleteProject(id: number | string): Promise<void> {
  await client.delete(`/projects/${id}`)
}
