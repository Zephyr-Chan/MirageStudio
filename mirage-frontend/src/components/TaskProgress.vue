<script setup lang="ts">
import { computed } from 'vue'
import type { TaskStatus } from '@/types'

const props = defineProps<{
  status: TaskStatus
  progress: number
  message?: string
  log?: string[]
  compact?: boolean
}>()

const statusMeta: Record<TaskStatus, { label: string; cls: string }> = {
  PENDING: { label: '排队中', cls: 'tag-pending' },
  RUNNING: { label: '运行中', cls: 'tag-running' },
  SUCCESS: { label: '已完成', cls: 'tag-success' },
  FAILED: { label: '已失败', cls: 'tag-failed' },
  CANCELLED: { label: '已取消', cls: 'tag-idle' },
}

const meta = computed(() => statusMeta[props.status])
const isActive = computed(() => props.status === 'PENDING' || props.status === 'RUNNING')
const pct = computed(() => Math.min(100, Math.max(0, props.progress)))
</script>

<template>
  <div class="task-progress" :class="{ compact }">
    <div class="tp-head">
      <span class="tag" :class="meta.cls">{{ meta.label }}</span>
      <span class="tp-pct">{{ pct }}%</span>
    </div>

    <div class="tp-bar">
      <div
        class="tp-fill"
        :class="{ indeterminate: status === 'PENDING' }"
        :style="{ width: status === 'PENDING' ? '100%' : pct + '%' }"
      ></div>
    </div>

    <p v-if="message" class="tp-msg">{{ message }}</p>

    <div v-if="log && log.length && !compact" class="tp-log">
      <div v-for="(line, i) in log.slice(-80)" :key="i" class="log-line">
        <span class="log-idx">{{ String(i + 1).padStart(3, '0') }}</span>
        <span class="log-txt">{{ line }}</span>
      </div>
    </div>

    <div v-if="isActive" class="tp-running-hint">
      <span class="spinner"></span>
      <span>正在处理，进度将通过实时通道更新</span>
    </div>
  </div>
</template>

<style scoped>
.task-progress {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.tp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.tp-pct {
  font-family: var(--font-mono);
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text);
}

.tp-bar {
  height: 6px;
  background: var(--surface-3);
  border-radius: 4px;
  overflow: hidden;
}
.tp-fill {
  height: 100%;
  background: var(--accent);
  border-radius: 4px;
  transition: width 0.4s var(--ease);
}
.tp-fill.indeterminate {
  background: var(--surface-3);
  position: relative;
  overflow: hidden;
}
.tp-fill.indeterminate::after {
  content: '';
  position: absolute;
  inset: 0;
  width: 40%;
  background: var(--accent);
  border-radius: 4px;
  animation: indet 1.2s var(--ease) infinite;
}
@keyframes indet {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(350%); }
}

.tp-msg {
  font-size: 12px;
  color: var(--text-dim);
}

.tp-log {
  margin-top: 4px;
  max-height: 160px;
  overflow-y: auto;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 8px 10px;
  font-family: var(--font-mono);
  font-size: 11px;
}
.log-line {
  display: flex;
  gap: 10px;
  padding: 1px 0;
  color: var(--text-dim);
}
.log-idx {
  color: var(--text-faint);
  flex-shrink: 0;
}
.log-txt {
  white-space: pre-wrap;
  word-break: break-all;
}

.tp-running-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11.5px;
  color: var(--text-dim);
}

.compact .tp-log,
.compact .tp-running-hint {
  display: none;
}
</style>
