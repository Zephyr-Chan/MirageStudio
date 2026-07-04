<script setup lang="ts">
/* ============================================================
   AgentPanel — 自然语言下达目标 + ReAct 轨迹流可视化
   ============================================================ */
import { ref, nextTick, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { sendGoal, getTrace } from '@/api/agent'
import type { ChatMessage, ReActStep } from '@/types'

const props = defineProps<{
  projectId?: string | number
}>()

const route = useRoute()
const pid = computed(() => props.projectId ?? (route.params.id as string))

const messages = ref<ChatMessage[]>([])
const steps = ref<ReActStep[]>([])
const input = ref('')
const sending = ref(false)
const traceRunning = ref(false)
const bodyRef = ref<HTMLDivElement | null>(null)

let pollTimer: number | null = null
let seenStepIds = new Set<string>()

const suggestions = [
  '将模型旋转到正面视角',
  '生成赛博朋克风格特效',
  '优化模型表面平滑度',
]

function uid() {
  return Math.random().toString(36).slice(2, 10)
}

async function scrollToBottom() {
  await nextTick()
  if (bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight
}

async function onSend(text?: string) {
  const content = (text ?? input.value).trim()
  if (!content || sending.value) return
  input.value = ''
  sending.value = true
  steps.value = []
  seenStepIds = new Set()

  messages.value.push({
    id: uid(),
    role: 'user',
    content,
    createdAt: Date.now(),
  })
  await scrollToBottom()

  try {
    const res = await sendGoal(pid.value, content, messages.value.slice(-6))
    messages.value.push(res.reply)
    await scrollToBottom()
    // 轮询 ReAct 轨迹
    traceRunning.value = true
    pollTrace(res.taskId)
  } catch {
    /* 拦截器已提示 */
  } finally {
    sending.value = false
  }
}

function pollTrace(taskId: string) {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = window.setInterval(async () => {
    try {
      const trace = await getTrace(taskId)
      let added = false
      for (const s of trace) {
        if (!seenStepIds.has(s.id)) {
          seenStepIds.add(s.id)
          steps.value.push(s)
          added = true
        } else {
          // 更新已有步骤状态
          const idx = steps.value.findIndex((x) => x.id === s.id)
          if (idx >= 0) steps.value[idx] = { ...steps.value[idx], ...s }
        }
      }
      if (added) scrollToBottom()
      // 最后一步完成则停止
      const last = trace[trace.length - 1]
      if (last && (last.status === 'done' || last.type === 'observation')) {
        const allDone = trace.every((s) => s.status === 'done')
        if (allDone) {
          clearInterval(pollTimer!)
          pollTimer = null
          traceRunning.value = false
        }
      }
    } catch {
      /* 忽略轮询错误 */
    }
  }, 1000)

  // 超时保护：60s 后停止
  setTimeout(() => {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
      traceRunning.value = false
    }
  }, 60000)
}

const stepIcon: Record<ReActStep['type'], string> = {
  thought: 'T',
  action: 'A',
  observation: 'O',
}
const stepColor: Record<ReActStep['type'], string> = {
  thought: 'var(--accent)',
  action: 'var(--warning)',
  observation: 'var(--success)',
}
const stepLabel: Record<ReActStep['type'], string> = {
  thought: '思考',
  action: '动作',
  observation: '观察',
}

onMounted(() => {
  messages.value.push({
    id: uid(),
    role: 'assistant',
    content: '你好，我是 MirageStudio Agent。用自然语言告诉我你的创作目标，我会拆解为可执行步骤。',
    createdAt: Date.now(),
  })
})
</script>

<template>
  <div class="agent-panel">
    <div class="panel-head">
      <div class="ph-left">
        <span class="ph-dot" :class="{ live: traceRunning }"></span>
        <span class="ph-title">Agent</span>
      </div>
      <span class="ph-sub">自然语言 · ReAct</span>
    </div>

    <div ref="bodyRef" class="panel-body">
      <!-- 对话 -->
      <div
        v-for="m in messages"
        :key="m.id"
        class="msg"
        :class="m.role"
      >
        <div class="msg-bubble">{{ m.content }}</div>
      </div>

      <!-- ReAct 轨迹流 -->
      <div v-if="steps.length" class="react-stream">
        <div class="stream-title">
          <span>ReAct 轨迹</span>
          <span v-if="traceRunning" class="stream-live">
            <span class="spinner"></span>运行中
          </span>
        </div>
        <div class="stream-line"></div>
        <transition-group name="step" tag="div" class="step-list">
          <div v-for="s in steps" :key="s.id" class="step-item">
            <div class="step-marker" :style="{ background: stepColor[s.type] }">
              {{ stepIcon[s.type] }}
            </div>
            <div class="step-body">
              <div class="step-head">
                <span class="step-type" :style="{ color: stepColor[s.type] }">{{ stepLabel[s.type] }}</span>
                <span v-if="s.tool" class="step-tool">{{ s.tool }}</span>
                <span v-if="s.status === 'running'" class="spinner step-spin"></span>
              </div>
              <p class="step-content">{{ s.content }}</p>
            </div>
          </div>
        </transition-group>
      </div>
    </div>

    <!-- 建议 -->
    <div v-if="!messages.length || messages.length <= 1" class="suggest-row">
      <button
        v-for="s in suggestions"
        :key="s"
        class="suggest-chip"
        @click="onSend(s)"
      >{{ s }}</button>
    </div>

    <!-- 输入 -->
    <div class="panel-input">
      <textarea
        v-model="input"
        class="input-box"
        placeholder="描述你的创作目标…"
        rows="2"
        @keydown.enter.exact.prevent="onSend()"
        @keydown.shift.enter="input += '\n'"
      ></textarea>
      <button class="send-btn" :disabled="sending || !input.trim()" @click="onSend()">
        <svg v-if="!sending" width="15" height="15" viewBox="0 0 16 16" fill="none">
          <path d="M2 8h10M8 4l4 4-4 4" stroke="currentColor" stroke-width="1.6"
            stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        <span v-else class="spinner"></span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.agent-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--surface);
}

.panel-head {
  height: 42px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  border-bottom: 1px solid var(--border);
}
.ph-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ph-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--text-faint);
}
.ph-dot.live {
  background: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
  animation: pulse 1.4s ease infinite;
}
.ph-title {
  font-size: 13px;
  font-weight: 600;
}
.ph-sub {
  font-size: 11px;
  color: var(--text-faint);
}

.panel-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.msg {
  display: flex;
}
.msg.user {
  justify-content: flex-end;
}
.msg-bubble {
  max-width: 86%;
  padding: 8px 12px;
  border-radius: 12px;
  font-size: 12.5px;
  line-height: 1.5;
}
.msg.user .msg-bubble {
  background: var(--accent);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.msg.assistant .msg-bubble {
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-bottom-left-radius: 4px;
}

.react-stream {
  margin-top: 4px;
  position: relative;
}
.stream-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-dim);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 10px;
}
.stream-live {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--accent);
  font-weight: 500;
  text-transform: none;
  letter-spacing: 0;
}
.step-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  position: relative;
  padding-left: 2px;
}

.step-item {
  display: flex;
  gap: 10px;
  padding: 6px 0;
}
.step-marker {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
}
.step-body {
  flex: 1;
  min-width: 0;
}
.step-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.step-type {
  font-size: 11px;
  font-weight: 600;
}
.step-tool {
  font-size: 10.5px;
  color: var(--text-dim);
  font-family: var(--font-mono);
  background: var(--surface-2);
  padding: 1px 5px;
  border-radius: 3px;
}
.step-spin {
  width: 11px;
  height: 11px;
}
.step-content {
  font-size: 12px;
  color: var(--text-dim);
  margin-top: 2px;
  line-height: 1.5;
}

/* 流式入场动画 */
.step-enter-active {
  animation: step-in 0.4s var(--ease) both;
}

.suggest-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 0 14px 8px;
}
.suggest-chip {
  font-size: 11.5px;
  padding: 4px 10px;
  border-radius: 14px;
  background: var(--surface-2);
  border: 1px solid var(--border);
  color: var(--text-dim);
  transition: all 0.15s var(--ease);
}
.suggest-chip:hover {
  color: var(--accent);
  border-color: var(--accent-line);
  background: var(--accent-soft);
}

.panel-input {
  flex-shrink: 0;
  display: flex;
  gap: 8px;
  padding: 10px 14px 14px;
  border-top: 1px solid var(--border);
}
.input-box {
  flex: 1;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 8px 10px;
  font-size: 12.5px;
  line-height: 1.5;
  resize: none;
  font-family: inherit;
}
.input-box:focus {
  outline: none;
  border-color: var(--accent-line);
}
.send-btn {
  width: 34px;
  height: 34px;
  align-self: flex-end;
  border-radius: var(--radius-sm);
  background: var(--accent);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 0.15s var(--ease);
}
.send-btn:not(:disabled):hover {
  background: var(--accent-hover);
}
.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
