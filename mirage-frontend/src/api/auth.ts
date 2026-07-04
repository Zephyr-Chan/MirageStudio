import client from './client'
import type { User } from '@/types'

export interface LoginPayload {
  username: string
  password: string
}

export interface RegisterPayload {
  username: string
  email?: string
  password: string
}

/** 后端 /api/auth/login 和 /register 返回的原始数据 (R.data 解包后) */
interface AuthResponse {
  userId: number
  username: string
  role?: string
  token: string
}

export interface AuthResult {
  token: string
  user: User
}

/** 将后端扁平结构映射为前端期望的 { token, user } 格式 */
function mapAuthResult(raw: AuthResponse): AuthResult {
  return {
    token: raw.token,
    user: {
      id: raw.userId,
      username: raw.username,
      email: '',
      role: raw.role,
    },
  }
}

export async function login(payload: LoginPayload): Promise<AuthResult> {
  const { data } = await client.post<AuthResponse>('/auth/login', payload)
  return mapAuthResult(data)
}

export async function register(payload: RegisterPayload): Promise<AuthResult> {
  const { data } = await client.post<AuthResponse>('/auth/register', payload)
  return mapAuthResult(data)
}
