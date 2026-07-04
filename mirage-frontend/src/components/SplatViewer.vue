<script setup lang="ts">
/* ============================================================
   SplatViewer — 3D 高斯泼溅查看器
   · Three.js 场景 + OrbitControls 旋转/缩放/平移
   · 预留 SplatRendererAdapter 接口，可插入 GaussianSplats3D
   · 当前以占位几何体渲染，保证无 .splat 数据时也可交互
   · 截屏：renderer.domElement → toBlob → 上传
   ============================================================ */
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { uploadScreenshot } from '@/api/effect'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  splatUrl?: string
  projectId?: string | number
}>()

const emit = defineEmits<{
  (e: 'screenshot', url: string): void
  (e: 'camera-change', pos: { x: number; y: number; z: number }): void
}>()

const containerRef = ref<HTMLDivElement | null>(null)
const loading = ref(false)
const hasContent = ref(false)

let scene: THREE.Scene
let camera: THREE.PerspectiveCamera
let renderer: THREE.WebGLRenderer
let controls: OrbitControls
let placeholder: THREE.Mesh
let rafId = 0
let resizeObserver: ResizeObserver | null = null
let adapter: SplatRendererAdapter | null = null

/* ---------- 适配器接口：未来接入 GaussianSplats3D ---------- */
interface SplatRendererAdapter {
  /** 加载 .splat / .ply 资源并挂载到 scene */
  load(url: string, scene: THREE.Scene): Promise<void>
  /** 资源释放 */
  dispose(): void
}

/* ---------- 占位渲染器（无 splat 数据时） ---------- */
function createPlaceholder(): THREE.Mesh {
  const geo = new THREE.IcosahedronGeometry(1.2, 1)
  const mat = new THREE.MeshStandardMaterial({
    color: 0x5b5bd6,
    metalness: 0.3,
    roughness: 0.45,
    flatShading: true,
    transparent: true,
    opacity: 0.92,
  })
  const mesh = new THREE.Mesh(geo, mat)
  // 线框叠加，强化“占位/待加载”语义
  const wire = new THREE.LineSegments(
    new THREE.WireframeGeometry(geo),
    new THREE.LineBasicMaterial({ color: 0x6f6fe0, transparent: true, opacity: 0.4 })
  )
  mesh.add(wire)
  return mesh
}

function initScene() {
  const el = containerRef.value!
  const w = el.clientWidth || 800
  const h = el.clientHeight || 600

  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x0f0f12)

  camera = new THREE.PerspectiveCamera(50, w / h, 0.1, 1000)
  camera.position.set(3, 2.2, 4)

  renderer = new THREE.WebGLRenderer({
    antialias: true,
    preserveDrawingBuffer: true, // 保证 toBlob 截屏可用
    alpha: false,
  })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.setSize(w, h)
  el.appendChild(renderer.domElement)

  // 光照
  const amb = new THREE.AmbientLight(0xffffff, 0.55)
  scene.add(amb)
  const dir = new THREE.DirectionalLight(0xffffff, 0.9)
  dir.position.set(4, 6, 3)
  scene.add(dir)
  const rim = new THREE.DirectionalLight(0x5b5bd6, 0.5)
  rim.position.set(-4, -2, -3)
  scene.add(rim)

  // 网格地板
  const grid = new THREE.GridHelper(20, 40, 0x26262e, 0x1c1c22)
  ;(grid.material as THREE.Material).transparent = true
  ;(grid.material as THREE.Material).opacity = 0.6
  scene.add(grid)

  // 占位几何
  placeholder = createPlaceholder()
  scene.add(placeholder)

  // 控制器
  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.minDistance = 1.5
  controls.maxDistance = 30
  controls.addEventListener('change', () => {
    emit('camera-change', {
      x: camera.position.x,
      y: camera.position.y,
      z: camera.position.z,
    })
  })

  // 自适应
  resizeObserver = new ResizeObserver(() => onResize())
  resizeObserver.observe(el)

  animate()
}

function animate() {
  rafId = requestAnimationFrame(animate)
  if (placeholder.visible) {
    placeholder.rotation.y += 0.0035
  }
  controls.update()
  renderer.render(scene, camera)
}

function onResize() {
  const el = containerRef.value
  if (!el) return
  const w = el.clientWidth
  const h = el.clientHeight
  if (!w || !h) return
  camera.aspect = w / h
  camera.updateProjectionMatrix()
  renderer.setSize(w, h)
}

/* ---------- 加载 splat 资源 ---------- */
async function loadSplat(url: string) {
  loading.value = true
  try {
    // 优先尝试 GaussianSplats3D 适配器（动态导入，失败则回退占位）
    adapter = await tryCreateSplatsAdapter()
    if (adapter) {
      await adapter.load(url, scene)
      placeholder.visible = false
      hasContent.value = true
    } else {
      // 无适配器：保留占位，标记“预览模式”
      placeholder.visible = true
      hasContent.value = false
      ElMessage.info('高斯泼溅渲染器未安装，当前为占位预览')
    }
  } catch (e) {
    console.warn('[SplatViewer] load failed', e)
    placeholder.visible = true
    ElMessage.warning('模型加载失败，显示占位预览')
  } finally {
    loading.value = false
  }
}

/** 尝试动态加载 @mkkelogg/gaussian-splats-3d 适配器 */
async function tryCreateSplatsAdapter(): Promise<SplatRendererAdapter | null> {
  try {
    // 使用变量引用，让 TS 将动态导入视为 any，避免对未安装的可选依赖做静态解析
    const specifier = '@mkkelogg/gaussian-splats-3d'
    const mod: any = await import(/* @vite-ignore */ specifier)
    const Viewer = mod.default ?? mod.Viewer
    if (!Viewer) return null
    return {
      async load(url: string, scn: THREE.Scene) {
        const viewer = new Viewer({ camera, renderer })
        await viewer.loadSplat(url)
        // GaussianSplats3D 自管理渲染循环，此处同步 scene 引用
        scn.add(viewer.scene || (viewer as any).splatMesh || new THREE.Object3D())
        ;(this as any)._viewer = viewer
      },
      dispose() {
        ;(this as any)._viewer?.dispose?.()
      },
    }
  } catch {
    return null
  }
}

/* ---------- 截屏 ---------- */
async function takeScreenshot(): Promise<string | null> {
  const canvas = renderer.domElement
  // 确保最新一帧已绘制
  renderer.render(scene, camera)
  const blob: Blob | null = await new Promise((resolve) =>
    canvas.toBlob((b) => resolve(b), 'image/png')
  )
  if (!blob) return null
  const localUrl = URL.createObjectURL(blob)
  emit('screenshot', localUrl)
  // 上传到后端作为特效输入参考
  if (props.projectId) {
    try {
      const res = await uploadScreenshot(props.projectId, blob)
      return res.storageKey ? `/api/assets/${res.id}/download-url` : localUrl
    } catch {
      /* 上传失败不影响本地预览 */
    }
  }
  return localUrl
}

/* ---------- 视图重置 ---------- */
function resetView() {
  camera.position.set(3, 2.2, 4)
  controls.target.set(0, 0, 0)
  controls.update()
}

defineExpose({ takeScreenshot, resetView })

watch(
  () => props.splatUrl,
  (url) => {
    if (url) loadSplat(url)
  }
)

onMounted(() => {
  initScene()
  if (props.splatUrl) loadSplat(props.splatUrl)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(rafId)
  resizeObserver?.disconnect()
  controls?.dispose()
  adapter?.dispose()
  renderer?.dispose()
  if (renderer?.domElement.parentElement) {
    renderer.domElement.parentElement.removeChild(renderer.domElement)
  }
})
</script>

<template>
  <div class="splat-viewer">
    <div ref="containerRef" class="viewer-canvas"></div>

    <!-- 工具栏 -->
    <div class="viewer-toolbar">
      <button class="vt-btn" title="重置视角" @click="resetView">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
          <path d="M2.5 8a5.5 5.5 0 1 0 1.7-3.96M2.5 3v3h3" stroke="currentColor" stroke-width="1.4"
            stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>
      <button class="vt-btn primary" title="截屏并发送至特效面板" @click="takeScreenshot">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
          <path d="M6 3H3v10h10v-3M6 3l.8-1.2h2.4L10 3M6 3h4M10 10l4-4m0 0v3.5M14 6h-3.5"
            stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        <span>截屏</span>
      </button>
    </div>

    <!-- 状态浮层 -->
    <div v-if="loading" class="viewer-overlay">
      <span class="spinner"></span>
      <span>加载模型中…</span>
    </div>
    <div v-if="!loading && !hasContent" class="viewer-hint">
      <span>占位预览 · 左键旋转 / 右键平移 / 滚轮缩放</span>
    </div>
  </div>
</template>

<style scoped>
.splat-viewer {
  position: relative;
  width: 100%;
  height: 100%;
  background: #0f0f12;
  overflow: hidden;
}
.viewer-canvas {
  width: 100%;
  height: 100%;
}
.viewer-canvas :deep(canvas) {
  display: block;
}

.viewer-toolbar {
  position: absolute;
  bottom: 14px;
  right: 14px;
  display: flex;
  gap: 6px;
}
.vt-btn {
  height: 32px;
  padding: 0 10px;
  border-radius: var(--radius-sm);
  background: var(--overlay);
  backdrop-filter: blur(8px);
  border: 1px solid var(--border);
  color: var(--text-dim);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  transition: all 0.15s var(--ease);
}
.vt-btn:hover {
  color: var(--text);
  border-color: var(--border-strong);
}
.vt-btn.primary {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}
.vt-btn.primary:hover {
  background: var(--accent-hover);
}

.viewer-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: var(--overlay);
  backdrop-filter: blur(2px);
  font-size: 13px;
  color: var(--text-dim);
}
.viewer-hint {
  position: absolute;
  top: 12px;
  left: 12px;
  font-size: 11px;
  color: var(--text-faint);
  background: var(--overlay);
  backdrop-filter: blur(6px);
  padding: 4px 9px;
  border-radius: 5px;
}
</style>
