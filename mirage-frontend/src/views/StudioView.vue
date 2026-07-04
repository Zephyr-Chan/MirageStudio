<script setup lang="ts">
/* ============================================================
   StudioView — 3D 工作室主页面
   布局：左 Agent 面板 | 中 3D 查看器 | 右 特效编辑器
         底 时间轴
   ============================================================ */
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { useTaskStore } from '@/stores/task'
import SplatViewer from '@/components/SplatViewer.vue'
import EffectEditor from '@/components/EffectEditor.vue'
import AgentPanel from '@/components/AgentPanel.vue'
import Timeline from '@/components/Timeline.vue'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const taskStore = useTaskStore()

const projectId = computed(() => route.params.id as string)
const project = computed(() => projectStore.current)

const splatViewerRef = ref<InstanceType<typeof SplatViewer> | null>(null)
const screenshotUrl = ref<string | null>(null)

const leftCollapsed = ref(false)
const rightCollapsed = ref(false)

onMounted(async () => {
  try {
    const p = await projectStore.fetchOne(projectId.value)
    // 若项目还在重建，提示回向导
    if (p?.status === 'DRAFT' || p?.status === 'RECONSTRUCTING') {
      // 仍允许进入预览
    }
  } catch {
    /* ignore */
  }
  taskStore.connect()
})

function onScreenshot(url: string) {
  screenshotUrl.value = url
}

function onCameraChange(_pos: { x: number; y: number; z: number }) {
  // 可在此同步到时间轴关键帧编辑；当前保留钩子
}

function onTimelineSeek(_time: number) {
  // 播放头寻址，可触发相机插值移动
}

function onKeyframeSelect(kf: { camera: { x: number; y: number; z: number } }) {
  // 预留：将相机移动到关键帧位置
  void kf
}

function captureNow() {
  splatViewerRef.value?.takeScreenshot()
}
</script>

<template>
  <div class="studio">
    <!-- 顶部工具条 -->
    <div class="studio-toolbar">
      <div class="tb-left">
        <button class="icon-btn" title="返回项目" @click="router.push('/projects')">
          <svg width="15" height="15" viewBox="0 0 16 16" fill="none">
            <path d="M10 4L6 8l4 4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"
              stroke-linejoin="round" />
          </svg>
        </button>
        <span class="tb-project">{{ project?.name ?? '加载中…' }}</span>
        <span
          v-if="project?.status"
          class="tag"
          :class="project.status === 'READY' ? 'tag-success' : 'tag-running'"
        >
          {{ project.status === 'READY' ? '就绪' : project.status === 'RECONSTRUCTING' ? '重建中' : project.status }}
        </span>
      </div>
      <div class="tb-right">
        <button class="btn btn-ghost btn-sm" @click="captureNow">
          <svg width="13" height="13" viewBox="0 0 16 16" fill="none">
            <path d="M6 3H3v10h10v-3M6 3l.8-1.2h2.4L10 3M6 3h4M10 10l4-4m0 0v3.5M14 6h-3.5"
              stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          截屏
        </button>
        <button class="icon-btn" :class="{ active: !leftCollapsed }"
          title="切换 Agent 面板" @click="leftCollapsed = !leftCollapsed">
          <svg width="15" height="15" viewBox="0 0 16 16" fill="none">
            <rect x="2" y="3" width="12" height="10" rx="1.5" stroke="currentColor" stroke-width="1.3" />
            <line x1="6" y1="3" x2="6" y2="13" stroke="currentColor" stroke-width="1.3" />
          </svg>
        </button>
        <button class="icon-btn" :class="{ active: !rightCollapsed }"
          title="切换特效面板" @click="rightCollapsed = !rightCollapsed">
          <svg width="15" height="15" viewBox="0 0 16 16" fill="none">
            <rect x="2" y="3" width="12" height="10" rx="1.5" stroke="currentColor" stroke-width="1.3" />
            <line x1="10" y1="3" x2="10" y2="13" stroke="currentColor" stroke-width="1.3" />
          </svg>
        </button>
      </div>
    </div>

    <!-- 主网格 -->
    <div class="studio-grid" :class="{ 'left-hidden': leftCollapsed, 'right-hidden': rightCollapsed }">
      <!-- 左：Agent 面板 -->
      <aside class="studio-left anim-fade-in">
        <AgentPanel :project-id="projectId" />
      </aside>

      <!-- 中：3D 查看器 -->
      <section class="studio-center">
        <SplatViewer
          ref="splatViewerRef"
          :splat-url="project?.splatUrl"
          :project-id="projectId"
          @screenshot="onScreenshot"
          @camera-change="onCameraChange"
        />
      </section>

      <!-- 右：特效编辑器 -->
      <aside class="studio-right anim-fade-in">
        <EffectEditor :project-id="projectId" :screenshot-url="screenshotUrl ?? undefined" />
      </aside>
    </div>

    <!-- 底：时间轴 -->
    <div class="studio-bottom">
      <Timeline @seek="onTimelineSeek" @select="onKeyframeSelect" />
    </div>
  </div>
</template>

<style scoped>
.studio {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.studio-toolbar {
  flex-shrink: 0;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  border-bottom: 1px solid var(--border);
  background: var(--bg);
}
.tb-left,
.tb-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tb-project {
  font-size: 13px;
  font-weight: 600;
  margin-left: 4px;
}
.icon-btn {
  width: 30px;
  height: 30px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-dim);
  transition: all 0.15s var(--ease);
}
.icon-btn:hover {
  background: var(--surface-2);
  color: var(--text);
}
.icon-btn.active {
  color: var(--accent);
  background: var(--accent-soft);
}
.btn-sm {
  height: 28px;
  padding: 0 10px;
  font-size: 12px;
}

.studio-grid {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 300px 1fr 340px;
  grid-template-rows: 1fr;
  transition: grid-template-columns 0.28s var(--ease);
}
.studio-grid.left-hidden {
  grid-template-columns: 0px 1fr 340px;
}
.studio-grid.right-hidden {
  grid-template-columns: 300px 1fr 0px;
}
.studio-grid.left-hidden.right-hidden {
  grid-template-columns: 0px 1fr 0px;
}

.studio-left,
.studio-right {
  min-width: 0;
  overflow: hidden;
  border-right: 1px solid var(--border);
}
.studio-right {
  border-right: none;
  border-left: 1px solid var(--border);
}

.studio-center {
  min-width: 0;
  min-height: 0;
  position: relative;
}

.studio-bottom {
  flex-shrink: 0;
  height: 64px;
  border-top: 1px solid var(--border);
}
</style>
