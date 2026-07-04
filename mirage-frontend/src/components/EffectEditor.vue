<script setup lang="ts">
/* ============================================================
   EffectEditor — 特效编辑器
   · 模板选择 · Prompt · Seed · ControlNet 强度 · 结果对比
   ============================================================ */
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useTaskStore } from '@/stores/task'
import * as effectApi from '@/api/effect'
import { ElMessage } from 'element-plus'
import type { EffectTemplate, EffectParams, TaskStatusMessage } from '@/types'

const props = defineProps<{
  projectId?: string | number
  /** 来自 3D 查看器截屏的参考图 URL */
  screenshotUrl?: string
}>()

const route = useRoute()
const taskStore = useTaskStore()
const pid = computed(() => props.projectId ?? (route.params.id as string))

const templates = ref<EffectTemplate[]>([])
const selectedTpl = ref<EffectTemplate | null>(null)
const params = ref<EffectParams>({
  templateId: '',
  prompt: '',
  seed: 42,
  controlnetStrength: 0.6,
  denoiseStrength: 0.5,
})
const generating = ref(false)
const resultUrl = ref<string | null>(null)
const compareBefore = ref<string | null>(null)
const comparePos = ref(50)
let unwatch: (() => void) | null = null

onBeforeUnmount(() => {
  unwatch?.()
})

// 内置模板（后端未返回时的兜底）
const fallbackTemplates: EffectTemplate[] = [
  { id: 'cyberpunk', name: '赛博朋克', description: '霓虹光泽与湿润反射', prompt: 'cyberpunk neon, wet reflective surfaces, moody' },
  { id: 'studio', name: '影棚质感', description: '柔光纯色背景商业摄影', prompt: 'studio lighting, softbox, clean background, commercial' },
  { id: 'claymation', name: '黏土动画', description: '哑光黏土材质卡通感', prompt: 'claymation, matte clay material, stop motion' },
  { id: 'gold', name: '鎏金', description: '抛光金属镀金质感', prompt: 'polished gold, metallic, luxurious' },
  { id: 'glass', name: '琉璃', description: '半透明玻璃折射', prompt: 'translucent glass, refraction, frosted' },
  { id: 'lowpoly', name: '低多边形', description: '几何化低面数风格', prompt: 'low poly, flat shading, stylized' },
]

onMounted(async () => {
  try {
    templates.value = await effectApi.listTemplates()
  } catch {
    templates.value = fallbackTemplates
  }
  if (templates.value.length) selectTemplate(templates.value[0])
})

function selectTemplate(t: EffectTemplate) {
  selectedTpl.value = t
  params.value.templateId = t.id
  if (!params.value.prompt) params.value.prompt = t.prompt
}

function randomSeed() {
  params.value.seed = Math.floor(Math.random() * 99999)
}

async function onGenerate() {
  if (!params.value.prompt.trim()) {
    ElMessage.warning('请输入 Prompt')
    return
  }
  generating.value = true
  resultUrl.value = null
  compareBefore.value = props.screenshotUrl ?? null
  try {
    const task = await effectApi.applyEffect(pid.value, { ...params.value })
    unwatch = taskStore.watchTask(task.taskId, (m: TaskStatusMessage) => {
      if (m.status === 'SUCCESS') {
        generating.value = false
        // 实际项目中从结果接口获取 URL，这里用截屏占位
        resultUrl.value = props.screenshotUrl ?? '/placeholder-after.png'
        ElMessage.success('特效生成完成')
      } else if (m.status === 'FAILED') {
        generating.value = false
        ElMessage.error('特效生成失败')
      }
    })
  } catch {
    generating.value = false
  }
}

watch(
  () => props.screenshotUrl,
  (url) => {
    if (url && !compareBefore.value) compareBefore.value = url
  }
)

function onCompareMove(e: MouseEvent) {
  const target = e.currentTarget as HTMLElement
  const rect = target.getBoundingClientRect()
  comparePos.value = Math.min(100, Math.max(0, ((e.clientX - rect.left) / rect.width) * 100))
}
</script>

<template>
  <div class="effect-editor">
    <div class="panel-head">
      <span class="ph-title">特效编辑器</span>
      <span class="ph-sub">模板 · 参数 · 对比</span>
    </div>

    <div class="editor-body">
      <!-- 模板选择 -->
      <div class="section">
        <label class="field-label">模板</label>
        <div class="tpl-grid">
          <button
            v-for="t in templates"
            :key="t.id"
            class="tpl-chip"
            :class="{ active: selectedTpl?.id === t.id }"
            @click="selectTemplate(t)"
          >
            <span class="tpl-name">{{ t.name }}</span>
            <span class="tpl-desc">{{ t.description }}</span>
          </button>
        </div>
      </div>

      <!-- Prompt -->
      <div class="section">
        <label class="field-label">Prompt</label>
        <textarea v-model="params.prompt" class="textarea" rows="3"
          placeholder="描述期望的视觉风格…"></textarea>
      </div>

      <!-- Seed -->
      <div class="section">
        <label class="field-label">Seed</label>
        <div class="seed-row">
          <input v-model.number="params.seed" type="number" class="input" />
          <button class="btn btn-ghost btn-sm" @click="randomSeed">
            <svg width="13" height="13" viewBox="0 0 16 16" fill="none">
              <path d="M2.5 8a5.5 5.5 0 1 0 1.7-3.96M2.5 3v3h3" stroke="currentColor"
                stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
        </div>
      </div>

      <!-- ControlNet 强度 -->
      <div class="section">
        <label class="field-label">
          ControlNet 强度 · {{ params.controlnetStrength.toFixed(2) }}
        </label>
        <input v-model.number="params.controlnetStrength" type="range" min="0" max="1" step="0.05"
          class="slider" />
      </div>

      <!-- Denoise 强度 -->
      <div class="section">
        <label class="field-label">
          去噪强度 · {{ params.denoiseStrength.toFixed(2) }}
        </label>
        <input v-model.number="params.denoiseStrength" type="range" min="0" max="1" step="0.05"
          class="slider" />
      </div>

      <button class="btn btn-primary generate-btn" :disabled="generating" @click="onGenerate">
        <span v-if="generating" class="spinner"></span>
        {{ generating ? '生成中…' : '生成特效' }}
      </button>

      <!-- 结果对比 -->
      <div v-if="resultUrl" class="compare-section">
        <label class="field-label">结果对比</label>
        <div class="compare" @mousemove="onCompareMove">
          <img v-if="compareBefore" :src="compareBefore" class="cmp-img before" alt="before" />
          <img :src="resultUrl" class="cmp-img after" alt="after" />
          <div class="cmp-clip" :style="{ width: comparePos + '%' }">
            <img v-if="compareBefore" :src="compareBefore" class="cmp-img" alt="before-clip" />
          </div>
          <div class="cmp-divider" :style="{ left: comparePos + '%' }">
            <span class="cmp-handle"></span>
          </div>
          <span class="cmp-label left">原图</span>
          <span class="cmp-label right">特效</span>
        </div>
      </div>
      <div v-else-if="generating" class="compare-placeholder">
        <span class="spinner"></span>
        <span>正在生成特效…</span>
      </div>
      <div v-else-if="!props.screenshotUrl" class="compare-placeholder">
        <span>在 3D 查看器截屏后，此处显示对比</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.effect-editor {
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
.ph-title {
  font-size: 13px;
  font-weight: 600;
}
.ph-sub {
  font-size: 11px;
  color: var(--text-faint);
}

.editor-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tpl-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px;
}
.tpl-chip {
  text-align: left;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--surface-2);
  transition: all 0.15s var(--ease);
}
.tpl-chip:hover {
  border-color: var(--border-strong);
}
.tpl-chip.active {
  border-color: var(--accent-line);
  background: var(--accent-soft);
}
.tpl-name {
  display: block;
  font-size: 12px;
  font-weight: 600;
}
.tpl-desc {
  display: block;
  font-size: 10.5px;
  color: var(--text-dim);
  margin-top: 2px;
  line-height: 1.3;
}

.seed-row {
  display: flex;
  gap: 6px;
}
.seed-row .input {
  flex: 1;
}
.btn-sm {
  height: 34px;
  width: 34px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
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

.generate-btn {
  height: 36px;
  margin-top: 2px;
}

.compare-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.compare {
  position: relative;
  aspect-ratio: 16 / 10;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--surface-2);
  border: 1px solid var(--border);
  cursor: ew-resize;
  user-select: none;
}
.cmp-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  pointer-events: none;
}
.cmp-clip {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}
.cmp-clip .cmp-img {
  position: absolute;
  width: 100%;
  height: 100%;
}
.cmp-divider {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 2px;
  background: var(--accent);
  pointer-events: none;
}
.cmp-handle {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--accent);
  border: 2px solid var(--bg);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.4);
}
.cmp-label {
  position: absolute;
  top: 8px;
  font-size: 10.5px;
  font-weight: 600;
  padding: 2px 7px;
  border-radius: 4px;
  background: var(--overlay);
  color: var(--text-dim);
  pointer-events: none;
}
.cmp-label.left { left: 8px; }
.cmp-label.right { right: 8px; }

.compare-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  aspect-ratio: 16 / 10;
  border: 1px dashed var(--border);
  border-radius: var(--radius-sm);
  color: var(--text-faint);
  font-size: 12px;
}
</style>
