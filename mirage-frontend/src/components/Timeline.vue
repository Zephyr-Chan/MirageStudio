<script setup lang="ts">
/* ============================================================
   Timeline — 底部时间轴（相机关键帧）
   ============================================================ */
import { ref, computed } from 'vue'

interface Keyframe {
  id: string
  time: number // 0-100
  label: string
  camera: { x: number; y: number; z: number }
}

const props = defineProps<{
  /** 当前播放头位置 0-100 */
  playhead?: number
}>()

const emit = defineEmits<{
  (e: 'seek', time: number): void
  (e: 'select', kf: Keyframe): void
  (e: 'add', time: number): void
}>()

const keyframes = ref<Keyframe[]>([
  { id: 'k1', time: 0, label: '正面', camera: { x: 0, y: 1.5, z: 4 } },
  { id: 'k2', time: 25, label: '侧面', camera: { x: 4, y: 1.5, z: 0 } },
  { id: 'k3', time: 55, label: '俯视', camera: { x: 0, y: 5, z: 2 } },
  { id: 'k4', time: 80, label: '特写', camera: { x: 1.2, y: 0.8, z: 1.8 } },
])

const selectedId = ref<string | null>('k1')
const playhead = ref(props.playhead ?? 0)

const ticks = computed(() => {
  const arr: number[] = []
  for (let i = 0; i <= 10; i++) arr.push(i * 10)
  return arr
})

function onTrackClick(e: MouseEvent) {
  const track = e.currentTarget as HTMLElement
  const rect = track.getBoundingClientRect()
  const time = Math.min(100, Math.max(0, ((e.clientX - rect.left) / rect.width) * 100))
  playhead.value = time
  emit('seek', time)
}

function onKeyframeClick(kf: Keyframe, e: MouseEvent) {
  e.stopPropagation()
  selectedId.value = kf.id
  playhead.value = kf.time
  emit('select', kf)
  emit('seek', kf.time)
}

function onAddKeyframe(e: MouseEvent) {
  e.stopPropagation()
  const track = e.currentTarget as HTMLElement
  const rect = track.getBoundingClientRect()
  const time = Math.min(100, Math.max(0, ((e.clientX - rect.left) / rect.width) * 100))
  const kf: Keyframe = {
    id: 'k' + Date.now(),
    time,
    label: `关键帧 ${keyframes.value.length + 1}`,
    camera: { x: 0, y: 1.5, z: 4 },
  }
  keyframes.value.push(kf)
  keyframes.value.sort((a, b) => a.time - b.time)
  selectedId.value = kf.id
  emit('add', time)
}
</script>

<template>
  <div class="timeline">
    <div class="tl-head">
      <span class="tl-title">时间轴</span>
      <span class="tl-sub">相机关键帧 · 点击轨道添加</span>
    </div>

    <div class="tl-track" @click="onTrackClick" @dblclick="onAddKeyframe">
      <!-- 刻度 -->
      <div class="tl-ticks">
        <span v-for="t in ticks" :key="t" class="tl-tick" :style="{ left: t + '%' }">
          {{ t }}
        </span>
      </div>

      <!-- 关键帧之间的连线 -->
      <div class="tl-line"></div>

      <!-- 关键帧标记 -->
      <div
        v-for="kf in keyframes"
        :key="kf.id"
        class="tl-kf"
        :class="{ active: kf.id === selectedId }"
        :style="{ left: kf.time + '%' }"
        :title="kf.label"
        @click="onKeyframeClick(kf, $event)"
      >
        <svg width="11" height="11" viewBox="0 0 16 16" fill="none">
          <path d="M4 4l8 4-8 4z" fill="currentColor" />
        </svg>
        <span class="kf-label">{{ kf.label }}</span>
      </div>

      <!-- 播放头 -->
      <div class="tl-playhead" :style="{ left: playhead + '%' }">
        <div class="ph-line"></div>
        <div class="ph-knob"></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.timeline {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 16px;
  background: var(--surface);
  border-top: 1px solid var(--border);
}

.tl-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.tl-title {
  font-size: 11.5px;
  font-weight: 600;
}
.tl-sub {
  font-size: 11px;
  color: var(--text-faint);
}

.tl-track {
  position: relative;
  height: 30px;
  cursor: pointer;
}
.tl-ticks {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.tl-tick {
  position: absolute;
  top: 0;
  font-size: 9.5px;
  color: var(--text-faint);
  transform: translateX(-50%);
  font-family: var(--font-mono);
}
.tl-line {
  position: absolute;
  top: 18px;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--surface-3);
  border-radius: 1px;
}

.tl-kf {
  position: absolute;
  top: 12px;
  transform: translateX(-50%);
  width: 16px;
  height: 16px;
  border-radius: 5px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-dim);
  background: var(--surface-2);
  border: 1.5px solid var(--border-strong);
  transition: all 0.15s var(--ease);
  z-index: 2;
}
.tl-kf:hover {
  color: var(--text);
  transform: translateX(-50%) scale(1.1);
}
.tl-kf.active {
  color: #fff;
  background: var(--accent);
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
}
.kf-label {
  position: absolute;
  bottom: -16px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 9.5px;
  color: var(--text-faint);
  white-space: nowrap;
  opacity: 0;
  transition: opacity 0.15s var(--ease);
  pointer-events: none;
}
.tl-kf:hover .kf-label,
.tl-kf.active .kf-label {
  opacity: 1;
}

.tl-playhead {
  position: absolute;
  top: 0;
  bottom: 0;
  transform: translateX(-50%);
  pointer-events: none;
  z-index: 3;
}
.ph-line {
  position: absolute;
  top: 6px;
  bottom: 0;
  left: 50%;
  width: 1.5px;
  background: var(--accent);
  transform: translateX(-50%);
}
.ph-knob {
  position: absolute;
  top: 2px;
  left: 50%;
  transform: translateX(-50%);
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--accent);
  border: 2px solid var(--bg);
}
</style>
