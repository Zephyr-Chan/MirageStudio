<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useTaskStore } from '@/stores/task'
import { useProjectStore } from '@/stores/project'
import { ElMessage } from 'element-plus'
import TaskProgress from '@/components/TaskProgress.vue'
import type { Task, TaskType } from '@/types'

const taskStore = useTaskStore()
const projectStore = useProjectStore()

const selectedId = ref<number | null>(null)
const filterType = ref<TaskType | ''>('')

const typeLabel: Record<TaskType, string> = {
  RECONSTRUCTION: '重建',
  EFFECT: '特效',
  EXPORT: '导出',
}

const filtered = computed(() =>
  filterType.value ? taskStore.tasks.filter((t) => t.type === filterType.value) : taskStore.tasks
)

const selected = computed<Task | null>(
  () => taskStore.tasks.find((t) => t.id === selectedId.value) ?? null
)

onMounted(async () => {
  taskStore.connect()
  try {
    await taskStore.fetchAll()
    await projectStore.fetchAll()
    if (taskStore.tasks.length) selectedId.value = taskStore.tasks[0].id
  } catch {
    /* ignore */
  }
})

function projectOf(id: number) {
  return projectStore.projects.find((p) => p.id === id)?.name ?? `#${id}`
}

async function onCancel(t: Task) {
  try {
    await taskStore.cancel(t.id)
    ElMessage.success('已取消任务')
  } catch {
    /* ignore */
  }
}

function fmt(s: string) {
  if (!s) return '—'
  return new Date(s).toLocaleString('zh-CN', { hour12: false })
}
</script>

<template>
  <div class="tasks-view">
    <div class="tasks-head">
      <div>
        <h1 class="page-title">任务监控</h1>
        <p class="page-sub">重建与特效任务状态及日志</p>
      </div>
      <div class="conn-state">
        <span class="conn-dot" :class="{ on: taskStore.wsConnected }"></span>
        <span>{{ taskStore.wsConnected ? '实时已连接' : '未连接' }}</span>
        <button class="btn btn-ghost btn-sm" @click="taskStore.connect()">重连</button>
      </div>
    </div>

    <div class="filter-row">
      <button
        class="filter-chip"
        :class="{ active: filterType === '' }"
        @click="filterType = ''"
      >全部</button>
      <button
        v-for="(label, key) in typeLabel"
        :key="key"
        class="filter-chip"
        :class="{ active: filterType === key }"
        @click="filterType = key as TaskType"
      >{{ label }}</button>
    </div>

    <div class="tasks-body">
      <!-- 任务列表 -->
      <div class="task-list">
        <div v-if="!filtered.length" class="empty-mini">暂无任务</div>
        <div
          v-for="t in filtered"
          :key="t.id"
          class="task-row"
          :class="{ active: t.id === selectedId }"
          @click="selectedId = t.id"
        >
          <div class="row-top">
            <span class="row-type">{{ typeLabel[t.type] }}</span>
            <span class="row-project">{{ projectOf(t.projectId) }}</span>
            <span class="tag" :class="`tag-${t.status.toLowerCase()}`">
              {{ t.status === 'PENDING' ? '排队' : t.status === 'RUNNING' ? '运行' : t.status === 'SUCCESS' ? '完成' : t.status === 'FAILED' ? '失败' : '取消' }}
            </span>
          </div>
          <div class="row-bar">
            <div class="row-fill" :style="{ width: t.progress + '%' }"></div>
          </div>
          <div class="row-meta">
            <span>{{ t.progress }}%</span>
            <span>{{ fmt(t.updatedAt) }}</span>
          </div>
        </div>
      </div>

      <!-- 详情 -->
      <div class="task-detail">
        <template v-if="selected">
          <div class="detail-head">
            <div>
              <h2 class="detail-title">{{ typeLabel[selected.type] }}任务 #{{ selected.id }}</h2>
              <p class="detail-sub">项目 {{ projectOf(selected.projectId) }} · 创建于 {{ fmt(selected.createdAt) }}</p>
            </div>
            <button
              v-if="selected.status === 'PENDING' || selected.status === 'RUNNING'"
              class="btn btn-ghost btn-sm"
              @click="onCancel(selected)"
            >取消任务</button>
          </div>

          <TaskProgress
            :status="selected.status"
            :progress="selected.progress"
            :message="selected.message"
            :log="selected.log"
          />
        </template>
        <div v-else class="empty-detail">选择左侧任务查看详情</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tasks-view {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.tasks-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 22px 32px 16px;
  border-bottom: 1px solid var(--divider);
}
.page-title {
  font-size: 17px;
  font-weight: 600;
}
.page-sub {
  font-size: 12px;
  color: var(--text-dim);
  margin-top: 2px;
}
.conn-state {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-dim);
}
.conn-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--text-faint);
}
.conn-dot.on {
  background: var(--success);
  box-shadow: 0 0 0 3px var(--success-soft);
}
.btn-sm {
  height: 26px;
  padding: 0 10px;
  font-size: 11.5px;
}

.filter-row {
  display: flex;
  gap: 6px;
  padding: 12px 32px;
  border-bottom: 1px solid var(--divider);
}
.filter-chip {
  height: 26px;
  padding: 0 12px;
  border-radius: 6px;
  font-size: 12px;
  color: var(--text-dim);
  border: 1px solid var(--border);
  background: var(--surface);
  transition: all 0.15s var(--ease);
}
.filter-chip:hover {
  color: var(--text);
  border-color: var(--border-strong);
}
.filter-chip.active {
  color: var(--accent);
  background: var(--accent-soft);
  border-color: var(--accent-line);
}

.tasks-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 360px 1fr;
}

.task-list {
  border-right: 1px solid var(--divider);
  overflow-y: auto;
  padding: 8px;
}
.empty-mini {
  padding: 40px 16px;
  text-align: center;
  color: var(--text-faint);
  font-size: 12.5px;
}
.task-row {
  padding: 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background 0.15s var(--ease);
}
.task-row:hover {
  background: var(--surface);
}
.task-row.active {
  background: var(--surface-2);
}
.row-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.row-type {
  font-size: 12px;
  font-weight: 600;
}
.row-project {
  font-size: 11.5px;
  color: var(--text-dim);
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.row-bar {
  height: 4px;
  background: var(--surface-3);
  border-radius: 3px;
  overflow: hidden;
}
.row-fill {
  height: 100%;
  background: var(--accent);
  transition: width 0.4s var(--ease);
}
.row-meta {
  display: flex;
  justify-content: space-between;
  margin-top: 6px;
  font-size: 11px;
  color: var(--text-faint);
  font-family: var(--font-mono);
}

.task-detail {
  overflow-y: auto;
  padding: 24px 32px;
}
.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
}
.detail-title {
  font-size: 15px;
  font-weight: 600;
}
.detail-sub {
  font-size: 12px;
  color: var(--text-dim);
  margin-top: 3px;
}
.empty-detail {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-faint);
  font-size: 13px;
}
</style>
