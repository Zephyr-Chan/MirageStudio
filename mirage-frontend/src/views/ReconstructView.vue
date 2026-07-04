<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { useTaskStore } from '@/stores/task'
import * as reconApi from '@/api/reconstruction'
import { ElMessage } from 'element-plus'
import TaskProgress from '@/components/TaskProgress.vue'
import type { ReconstructionParams, TaskStatusMessage } from '@/types'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const taskStore = useTaskStore()

const projectId = computed(() => route.params.id as string)
const project = computed(() => projectStore.current)

const photos = ref<File[]>([])
const photoPreviews = ref<string[]>([])
const uploadedAssetIds = ref<number[]>([])
const uploading = ref(false)
const uploadPct = ref(0)
const dragOver = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const params = ref<ReconstructionParams>({
  quality: 'standard',
  featureMatching: true,
  denseReconstruction: true,
  resolutionScale: 1.0,
  iterations: 30000,
})

const running = ref(false)
const currentTaskId = ref<number | null>(null)
const status = ref<TaskStatusMessage['status']>('PENDING')
const progress = ref(0)
const message = ref<string | undefined>()
const log = ref<string[]>([])
let unwatch: (() => void) | null = null

const canStart = computed(
  () => photos.value.length >= 8 && !uploading.value && !running.value
)

onMounted(async () => {
  try {
    await projectStore.fetchOne(projectId.value)
  } catch {
    /* ignore */
  }
  taskStore.connect()
})

onUnmounted(() => {
  unwatch?.()
})

function onDrop(e: DragEvent) {
  e.preventDefault()
  dragOver.value = false
  const files = Array.from(e.dataTransfer?.files ?? []).filter((f) =>
    f.type.startsWith('image/')
  )
  addFiles(files)
}

function onPick(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files) {
    addFiles(Array.from(input.files).filter((f) => f.type.startsWith('image/')))
  }
  input.value = ''
}

function addFiles(files: File[]) {
  if (!files.length) return
  for (const f of files) {
    photos.value.push(f)
    photoPreviews.value.push(URL.createObjectURL(f))
  }
}

function removePhoto(idx: number) {
  URL.revokeObjectURL(photoPreviews.value[idx])
  photos.value.splice(idx, 1)
  photoPreviews.value.splice(idx, 1)
}

/** 上传照片（逐张上传到 MinIO） */
async function uploadAll() {
  if (!photos.value.length) return
  uploading.value = true
  let done = 0
  try {
    uploadedAssetIds.value = []
    for (const file of photos.value) {
      const asset = await reconApi.uploadPhoto(projectId.value, file, (p) => {
        uploadPct.value = Math.round(((done + p / 100) / photos.value.length) * 100)
      })
      uploadedAssetIds.value.push(asset.id)
      done++
    }
    uploadPct.value = 100
    ElMessage.success(`已上传 ${photos.value.length} 张照片`)
  } finally {
    uploading.value = false
  }
}

async function startReconstruction() {
  if (photos.value.length && uploadPct.value < 100) {
    await uploadAll()
  }
  running.value = true
  status.value = 'PENDING'
  progress.value = 0
  message.value = '正在提交重建任务…'
  log.value = []
  try {
    const result = await reconApi.startReconstruction(
      projectId.value,
      uploadedAssetIds.value,
      {
        iterations: params.value.iterations,
        resolution: params.value.resolutionScale.toString(),
      }
    )
    currentTaskId.value = result.taskId
    // 订阅该任务实时进度
    unwatch = taskStore.watchTask(result.taskId, (m: TaskStatusMessage) => {
      status.value = m.status
      progress.value = m.progress
      if (m.message) message.value = m.message
      if (m.logLine) log.value.push(m.logLine)
      if (m.status === 'SUCCESS') {
        ElMessage.success('重建完成')
        setTimeout(() => router.push(`/projects/${projectId.value}/studio`), 800)
      } else if (m.status === 'FAILED') {
        ElMessage.error('重建失败')
      }
    })
  } catch {
    running.value = false
  }
}

const qualityOptions = [
  { value: 'draft', label: '草稿', desc: '快速预览，低迭代' },
  { value: 'standard', label: '标准', desc: '均衡质量与速度' },
  { value: 'high', label: '高精度', desc: '最大质量，耗时较长' },
]
</script>

<template>
  <div class="recon-view">
    <div class="recon-head">
      <button class="back-btn" @click="router.push('/projects')">
        <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
          <path d="M10 4L6 8l4 4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"
            stroke-linejoin="round" />
        </svg>
      </button>
      <div>
        <h1 class="page-title">重建向导</h1>
        <p class="page-sub">{{ project?.name ?? '加载中…' }}</p>
      </div>
    </div>

    <div class="recon-body">
      <!-- 步骤 1：照片上传 -->
      <section class="recon-section">
        <div class="section-label">
          <span class="step-num">1</span>
          <h2>上传照片</h2>
          <span class="section-hint">至少 8 张，建议环绕物体多角度拍摄</span>
        </div>

        <div
          class="dropzone"
          :class="{ active: dragOver, filled: photos.length > 0 }"
          @dragover.prevent="dragOver = true"
          @dragleave="dragOver = false"
          @drop="onDrop"
          @click="fileInput?.click()"
        >
          <input
            ref="fileInput"
            type="file"
            multiple
            accept="image/*"
            class="hidden-input"
            @change="onPick"
          />
          <template v-if="!photos.length">
            <svg width="30" height="30" viewBox="0 0 32 32" fill="none">
              <path d="M16 21V7M10 13l6-6 6 6M6 24h20" stroke="var(--text-faint)" stroke-width="1.6"
                stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            <p class="dz-text">拖拽照片到此处，或点击选择</p>
            <p class="dz-sub">支持 JPG / PNG，单文件最大 50MB</p>
          </template>
          <template v-else>
            <div class="thumb-grid">
              <div v-for="(src, i) in photoPreviews" :key="i" class="thumb">
                <img :src="src" alt="" />
                <button class="thumb-rm" @click.stop="removePhoto(i)">
                  <svg width="10" height="10" viewBox="0 0 16 16" fill="none">
                    <path d="M4 4l8 8M12 4l-8 8" stroke="currentColor" stroke-width="2"
                      stroke-linecap="round" />
                  </svg>
                </button>
              </div>
              <div class="thumb add-more" @click.stop="fileInput?.click()">
                <svg width="20" height="20" viewBox="0 0 16 16" fill="none">
                  <path d="M8 3v10M3 8h10" stroke="var(--text-faint)" stroke-width="1.6"
                    stroke-linecap="round" />
                </svg>
              </div>
            </div>
          </template>
        </div>

        <div class="upload-row">
          <span class="count-text">{{ photos.length }} 张照片已选择</span>
          <button
            class="btn btn-ghost"
            :disabled="!photos.length || uploading"
            @click="uploadAll"
          >
            <span v-if="uploading" class="spinner"></span>
            {{ uploadPct === 100 ? '已上传' : '上传' }}
          </button>
        </div>
        <div v-if="uploading || uploadPct === 100" class="upload-bar">
          <div class="upload-fill" :style="{ width: uploadPct + '%' }"></div>
        </div>
      </section>

      <!-- 步骤 2：参数 -->
      <section class="recon-section">
        <div class="section-label">
          <span class="step-num">2</span>
          <h2>重建参数</h2>
        </div>

        <div class="param-grid">
          <div class="field">
            <label class="field-label">质量预设</label>
            <div class="quality-row">
              <button
                v-for="q in qualityOptions"
                :key="q.value"
                class="quality-opt"
                :class="{ active: params.quality === q.value }"
                @click="params.quality = q.value as ReconstructionParams['quality']"
              >
                <span class="q-name">{{ q.label }}</span>
                <span class="q-desc">{{ q.desc }}</span>
              </button>
            </div>
          </div>

          <div class="field">
            <label class="field-label">分辨率缩放 · {{ params.resolutionScale.toFixed(1) }}x</label>
            <input
              v-model.number="params.resolutionScale"
              type="range"
              min="0.5"
              max="2"
              step="0.1"
              class="slider"
            />
          </div>

          <div class="field">
            <label class="field-label">迭代次数 · {{ params.iterations.toLocaleString() }}</label>
            <input
              v-model.number="params.iterations"
              type="range"
              min="10000"
              max="100000"
              step="5000"
              class="slider"
            />
          </div>

          <div class="toggle-row">
            <label class="toggle">
              <input v-model="params.featureMatching" type="checkbox" />
              <span class="toggle-track"><span class="toggle-thumb"></span></span>
              <span class="toggle-label">特征匹配</span>
            </label>
            <label class="toggle">
              <input v-model="params.denseReconstruction" type="checkbox" />
              <span class="toggle-track"><span class="toggle-thumb"></span></span>
              <span class="toggle-label">稠密重建</span>
            </label>
          </div>
        </div>
      </section>

      <!-- 步骤 3：启动 + 进度 -->
      <section class="recon-section">
        <div class="section-label">
          <span class="step-num">3</span>
          <h2>启动重建</h2>
        </div>

        <div v-if="!running && progress === 0" class="start-block">
          <p class="start-hint">确认照片与参数后启动重建任务。任务进度将通过实时通道推送。</p>
          <button class="btn btn-primary start-btn" :disabled="!canStart" @click="startReconstruction">
            开始重建
          </button>
          <p v-if="photos.length < 8" class="warn-text">至少需要 8 张照片才能启动</p>
        </div>

        <TaskProgress
          v-else
          :status="status"
          :progress="progress"
          :message="message"
          :log="log"
        />

        <div v-if="status === 'SUCCESS'" class="next-block">
          <button class="btn btn-primary" @click="router.push(`/projects/${projectId}/studio`)">
            进入 3D 工作室 →
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.recon-view {
  height: 100%;
  overflow-y: auto;
}

.recon-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 22px 32px 18px;
  border-bottom: 1px solid var(--divider);
}
.back-btn {
  width: 30px;
  height: 30px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-dim);
  transition: background 0.15s var(--ease), color 0.15s var(--ease);
}
.back-btn:hover {
  background: var(--surface-2);
  color: var(--text);
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

.recon-body {
  padding: 24px 32px 48px;
  max-width: 820px;
}

.recon-section {
  padding-bottom: 28px;
  margin-bottom: 28px;
  border-bottom: 1px solid var(--divider);
}
.recon-section:last-child {
  border-bottom: none;
}

.section-label {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}
.step-num {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 11px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.section-label h2 {
  font-size: 13.5px;
  font-weight: 600;
}
.section-hint {
  font-size: 11.5px;
  color: var(--text-faint);
  margin-left: auto;
}

.dropzone {
  border: 1.5px dashed var(--border-strong);
  border-radius: var(--radius);
  min-height: 160px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: border-color 0.15s var(--ease), background 0.15s var(--ease);
  padding: 20px;
}
.dropzone.active {
  border-color: var(--accent);
  background: var(--accent-soft);
}
.dropzone.filled {
  cursor: default;
  align-items: stretch;
  justify-content: flex-start;
}
.dz-text {
  font-size: 13px;
  color: var(--text-dim);
}
.dz-sub {
  font-size: 11.5px;
  color: var(--text-faint);
}
.hidden-input {
  display: none;
}

.thumb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(76px, 1fr));
  gap: 8px;
  width: 100%;
}
.thumb {
  position: relative;
  aspect-ratio: 1;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--surface-2);
  border: 1px solid var(--border);
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.thumb-rm {
  position: absolute;
  top: 3px;
  right: 3px;
  width: 18px;
  height: 18px;
  border-radius: 4px;
  background: var(--overlay);
  color: var(--text);
  display: flex;
  align-items: center;
  justify-content: center;
}
.thumb-rm:hover {
  background: var(--danger);
}
.add-more {
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border-style: dashed;
}

.upload-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}
.count-text {
  font-size: 12px;
  color: var(--text-dim);
}
.upload-bar {
  height: 4px;
  background: var(--surface-3);
  border-radius: 3px;
  overflow: hidden;
  margin-top: 8px;
}
.upload-fill {
  height: 100%;
  background: var(--accent);
  transition: width 0.3s var(--ease);
}

.param-grid {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.quality-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.quality-opt {
  text-align: left;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--surface-2);
  transition: border-color 0.15s var(--ease), background 0.15s var(--ease);
}
.quality-opt:hover {
  border-color: var(--border-strong);
}
.quality-opt.active {
  border-color: var(--accent-line);
  background: var(--accent-soft);
}
.q-name {
  display: block;
  font-size: 12.5px;
  font-weight: 600;
}
.q-desc {
  display: block;
  font-size: 11px;
  color: var(--text-dim);
  margin-top: 2px;
}

.slider {
  -webkit-appearance: none;
  width: 100%;
  height: 4px;
  background: var(--surface-3);
  border-radius: 3px;
  outline: none;
}
.slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--accent);
  cursor: pointer;
}
.slider::-moz-range-thumb {
  width: 14px;
  height: 14px;
  border: none;
  border-radius: 50%;
  background: var(--accent);
  cursor: pointer;
}

.toggle-row {
  display: flex;
  gap: 24px;
}
.toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.toggle input {
  display: none;
}
.toggle-track {
  width: 32px;
  height: 18px;
  background: var(--surface-3);
  border-radius: 10px;
  position: relative;
  transition: background 0.15s var(--ease);
}
.toggle-thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 14px;
  height: 14px;
  background: var(--text-dim);
  border-radius: 50%;
  transition: transform 0.15s var(--ease), background 0.15s var(--ease);
}
.toggle input:checked + .toggle-track {
  background: var(--accent);
}
.toggle input:checked + .toggle-track .toggle-thumb {
  transform: translateX(14px);
  background: #fff;
}
.toggle-label {
  font-size: 12.5px;
  color: var(--text-dim);
}

.start-block {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
}
.start-hint {
  font-size: 12.5px;
  color: var(--text-dim);
}
.start-btn {
  height: 36px;
  padding: 0 22px;
}
.warn-text {
  font-size: 11.5px;
  color: var(--warning);
}

.next-block {
  margin-top: 16px;
}
</style>
