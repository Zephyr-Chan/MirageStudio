<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Project, ProjectStatus } from '@/types'

const router = useRouter()
const store = useProjectStore()

const dialogVisible = ref(false)
const creating = ref(false)
const newProject = ref({ name: '', description: '' })

const statusMap: Record<ProjectStatus, { label: string; cls: string }> = {
  DRAFT: { label: '草稿', cls: 'tag-idle' },
  RECONSTRUCTING: { label: '重建中', cls: 'tag-running' },
  READY: { label: '就绪', cls: 'tag-success' },
  EFFECT_APPLYING: { label: '特效中', cls: 'tag-running' },
  ARCHIVED: { label: '已归档', cls: 'tag-idle' },
}

onMounted(async () => {
  try {
    await store.fetchAll()
  } catch {
    /* 拦截器已提示 */
  }
})

function openCreate() {
  newProject.value = { name: '', description: '' }
  dialogVisible.value = true
}

async function onCreate() {
  if (!newProject.value.name.trim()) {
    ElMessage.warning('请输入项目名称')
    return
  }
  creating.value = true
  try {
    const p = await store.create(newProject.value)
    dialogVisible.value = false
    ElMessage.success('项目已创建')
    router.push(`/projects/${p.id}/reconstruct`)
  } finally {
    creating.value = false
  }
}

function openProject(p: Project) {
  if (p.status === 'DRAFT') {
    router.push(`/projects/${p.id}/reconstruct`)
  } else {
    router.push(`/projects/${p.id}/studio`)
  }
}

async function onDelete(p: Project, e: Event) {
  e.stopPropagation()
  try {
    await ElMessageBox.confirm(`确认删除项目「${p.name}」？此操作不可恢复。`, '删除项目', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await store.remove(p.id)
    ElMessage.success('已删除')
  } catch {
    /* 取消 */
  }
}

function fmtDate(s: string) {
  if (!s) return '—'
  const d = new Date(s)
  return `${d.getMonth() + 1}月${d.getDate()}日 ${String(d.getHours()).padStart(2, '0')}:${String(
    d.getMinutes()
  ).padStart(2, '0')}`
}
</script>

<template>
  <div class="projects-view">
    <div class="page-head">
      <div>
        <h1 class="page-title">项目</h1>
        <p class="page-sub">管理你的 3D 重建与特效创作项目</p>
      </div>
      <button class="btn btn-primary" @click="openCreate">
        <svg width="13" height="13" viewBox="0 0 16 16" fill="none">
          <path d="M8 3v10M3 8h10" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
        </svg>
        新建项目
      </button>
    </div>

    <div v-if="store.loading && !store.projects.length" class="empty-state">
      <span class="spinner"></span>
      <span>加载中…</span>
    </div>

    <div v-else-if="!store.projects.length" class="empty-state">
      <p>暂无项目</p>
      <button class="btn btn-ghost" @click="openCreate">创建第一个项目</button>
    </div>

    <div v-else class="project-grid">
      <div
        v-for="(p, i) in store.projects"
        :key="p.id"
        class="project-tile anim-fade-up"
        :style="{ animationDelay: `${i * 40}ms` }"
        @click="openProject(p)"
      >
        <div class="tile-thumb">
          <img v-if="p.thumbnailUrl" :src="p.thumbnailUrl" alt="" />
          <div v-else class="tile-placeholder">
            <svg width="28" height="28" viewBox="0 0 32 32" fill="none">
              <path d="M8 22 L16 8 L24 22 Z" stroke="var(--text-faint)" stroke-width="1.6"
                stroke-linejoin="round" />
            </svg>
          </div>
          <span class="tag" :class="statusMap[p.status].cls">{{ statusMap[p.status].label }}</span>
        </div>
        <div class="tile-body">
          <div class="tile-name">{{ p.name }}</div>
          <div class="tile-meta">
            <span>{{ p.photoCount }} 张照片</span>
            <span class="dot">·</span>
            <span>{{ fmtDate(p.updatedAt) }}</span>
          </div>
        </div>
        <button class="tile-delete" title="删除" @click="onDelete(p, $event)">
          <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
            <path d="M4 4l8 8M12 4l-8 8" stroke="currentColor" stroke-width="1.6"
              stroke-linecap="round" />
          </svg>
        </button>
      </div>
    </div>

    <!-- 新建对话框 -->
    <el-dialog v-model="dialogVisible" title="新建项目" width="420px" :close-on-click-modal="false">
      <div class="dialog-form">
        <div class="field">
          <label class="field-label">项目名称</label>
          <input v-model="newProject.name" class="input" placeholder="例如：展厅主展品" />
        </div>
        <div class="field">
          <label class="field-label">描述（可选）</label>
          <textarea v-model="newProject.description" class="textarea" placeholder="简述该项目的创作目标" />
        </div>
      </div>
      <template #footer>
        <button class="btn btn-ghost" @click="dialogVisible = false">取消</button>
        <button class="btn btn-primary" :disabled="creating" @click="onCreate">
          <span v-if="creating" class="spinner"></span>创建
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.projects-view {
  height: 100%;
  overflow-y: auto;
  padding: 28px 32px 40px;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 24px;
}
.page-title {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.01em;
}
.page-sub {
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--text-dim);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 80px 0;
  color: var(--text-dim);
  font-size: 13px;
}

.project-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px;
}

.project-tile {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.15s var(--ease), transform 0.15s var(--ease);
  position: relative;
}
.project-tile:hover {
  border-color: var(--border-strong);
  transform: translateY(-2px);
}

.tile-thumb {
  position: relative;
  aspect-ratio: 16 / 10;
  background: var(--surface-2);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.tile-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.tile-placeholder {
  color: var(--text-faint);
}
.tile-thumb .tag {
  position: absolute;
  top: 8px;
  left: 8px;
  background: var(--overlay);
  backdrop-filter: blur(4px);
}

.tile-body {
  padding: 12px 14px 14px;
}
.tile-name {
  font-size: 13.5px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tile-meta {
  margin-top: 4px;
  font-size: 11.5px;
  color: var(--text-dim);
  display: flex;
  align-items: center;
  gap: 5px;
}
.dot {
  color: var(--text-faint);
}

.tile-delete {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 24px;
  height: 24px;
  border-radius: 5px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-faint);
  opacity: 0;
  transition: opacity 0.15s var(--ease), background 0.15s var(--ease), color 0.15s var(--ease);
}
.project-tile:hover .tile-delete {
  opacity: 1;
}
.tile-delete:hover {
  background: var(--danger-soft);
  color: var(--danger);
}

.dialog-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 4px 0;
}
</style>
