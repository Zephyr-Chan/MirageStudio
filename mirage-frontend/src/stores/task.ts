import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Task, TaskStatusMessage } from '@/types'
import * as taskApi from '@/api/task'
import { tokenStorage } from '@/api/client'

/* ============================================================
   轻量 STOMP 客户端（原生 WebSocket）
   连接 /ws，订阅 /topic/tasks
   ============================================================ */

const STOMP_VERSION = '1.2'
const NULL = '\u0000'

interface StompHandlers {
  onMessage: (destination: string, body: string) => void
  onConnect: () => void
  onDisconnect: () => void
  onError: (msg: string) => void
}

class StompClient {
  private ws: WebSocket | null = null
  private subIdCounter = 0
  private connected = false
  private heartbeatTimer: number | null = null

  constructor(
    private url: string,
    private handlers: StompHandlers
  ) {}

  get isConnected() {
    return this.connected
  }

  connect(token: string) {
    // 带 token 的 STOMP 连接（通过子协议或查询参数）
    const url = `${this.url}?access_token=${encodeURIComponent(token)}`
    this.ws = new WebSocket(url)

    this.ws.onopen = () => {
      this.sendFrame('CONNECT', {
        'accept-version': STOMP_VERSION,
        host: 'localhost',
        'heart-beat': '10000,10000',
      })
    }

    this.ws.onmessage = (ev) => this.handleRaw(ev.data)

    this.ws.onclose = () => {
      this.connected = false
      this.stopHeartbeat()
      this.handlers.onDisconnect()
    }

    this.ws.onerror = () => {
      this.handlers.onError('WebSocket 连接失败')
    }
  }

  subscribe(destination: string): string {
    const id = `sub-${this.subIdCounter++}`
    this.sendFrame('SUBSCRIBE', { id, destination, ack: 'auto' })
    return id
  }

  disconnect() {
    if (this.ws && this.connected) {
      this.sendFrame('DISCONNECT', {})
    }
    this.stopHeartbeat()
    this.ws?.close()
    this.ws = null
    this.connected = false
  }

  private handleRaw(data: string) {
    // STOMP 帧：COMMAND\nheaders\n\nbody\u0000
    const frames = data.split(NULL)
    for (const frame of frames) {
      if (!frame.trim()) continue
      const lines = frame.split('\n')
      const command = lines[0].trim()
      const headers: Record<string, string> = {}
      let i = 1
      while (i < lines.length && lines[i].trim() !== '') {
        const idx = lines[i].indexOf(':')
        if (idx > 0) {
          headers[lines[i].slice(0, idx).trim()] = lines[i].slice(idx + 1).trim()
        }
        i++
      }
      const body = lines.slice(i + 1).join('\n')

      this.dispatch(command, headers, body)
    }
  }

  private dispatch(command: string, headers: Record<string, string>, body: string) {
    switch (command) {
      case 'CONNECTED':
        this.connected = true
        this.startHeartbeat()
        this.handlers.onConnect()
        break
      case 'MESSAGE':
        this.handlers.onMessage(headers['destination'] || '', body)
        break
      case 'ERROR':
        this.handlers.onError(body || headers['message'] || 'STOMP 错误')
        break
      case 'RECEIPT':
        break
    }
  }

  private sendFrame(command: string, headers: Record<string, string>, body = '') {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return
    let frame = command + '\n'
    for (const [k, v] of Object.entries(headers)) {
      frame += `${k}:${v}\n`
    }
    frame += '\n' + body + NULL
    this.ws.send(frame)
  }

  private startHeartbeat() {
    this.heartbeatTimer = window.setInterval(() => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        this.ws.send('\n')
      }
    }, 10000)
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }
}

/* ============================================================
   Task Store
   ============================================================ */

const WS_BASE = import.meta.env.VITE_WS_BASE
  ? import.meta.env.VITE_WS_BASE.replace(/^http/, 'ws')
  : `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${
      window.location.host
    }/ws`

export const useTaskStore = defineStore('task', () => {
  const tasks = ref<Task[]>([])
  const loading = ref(false)
  const wsConnected = ref(false)
  const wsError = ref<string | null>(null)

  // taskId -> 进度回调监听器（组件级订阅）
  const listeners = ref<Map<number, (m: TaskStatusMessage) => void>>(new Map())

  let stomp: StompClient | null = null

  const activeTasks = computed(() =>
    tasks.value.filter((t) => t.status === 'PENDING' || t.status === 'RUNNING')
  )

  async function fetchAll() {
    loading.value = true
    try {
      tasks.value = await taskApi.listTasks()
    } finally {
      loading.value = false
    }
  }

  function upsert(task: Task) {
    const idx = tasks.value.findIndex((t) => t.id === task.id)
    if (idx >= 0) {
      tasks.value[idx] = { ...tasks.value[idx], ...task }
    } else {
      tasks.value.unshift(task)
    }
  }

  function applyStatusMessage(msg: TaskStatusMessage) {
    const idx = tasks.value.findIndex((t) => t.id === msg.taskId)
    if (idx >= 0) {
      const t = tasks.value[idx]
      const next: Task = {
        ...t,
        status: msg.status,
        progress: msg.progress,
        message: msg.message ?? t.message,
      }
      if (msg.logLine) {
        next.log = [...(t.log ?? []), msg.logLine]
      }
      tasks.value[idx] = next
    }
    // 通知组件级监听器
    const cb = listeners.value.get(msg.taskId)
    if (cb) cb(msg)
  }

  /** 组件订阅单个任务进度 */
  function watchTask(taskId: number, cb: (m: TaskStatusMessage) => void) {
    listeners.value.set(taskId, cb)
    return () => listeners.value.delete(taskId)
  }

  /** 建立 STOMP 连接 */
  function connect() {
    const token = tokenStorage.get()
    if (!token) return
    if (stomp?.isConnected) return

    stomp = new StompClient(WS_BASE, {
      onConnect: () => {
        wsConnected.value = true
        wsError.value = null
        stomp?.subscribe('/topic/tasks')
      },
      onMessage: (_dest, body) => {
        try {
          const msg = JSON.parse(body) as TaskStatusMessage
          applyStatusMessage(msg)
        } catch {
          /* 忽略非 JSON 帧 */
        }
      },
      onDisconnect: () => {
        wsConnected.value = false
      },
      onError: (m) => {
        wsError.value = m
      },
    })
    stomp.connect(token)
  }

  function disconnect() {
    stomp?.disconnect()
    stomp = null
    wsConnected.value = false
  }

  async function cancel(id: number | string) {
    await taskApi.cancelTask(id)
    upsert({
      ...(tasks.value.find((t) => t.id === id) as Task),
      status: 'CANCELLED',
    })
  }

  return {
    tasks,
    loading,
    wsConnected,
    wsError,
    activeTasks,
    fetchAll,
    upsert,
    applyStatusMessage,
    watchTask,
    connect,
    disconnect,
    cancel,
  }
})
