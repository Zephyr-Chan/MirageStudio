/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, never>, Record<string, never>, unknown>
  export default component
}

/** 可选的高斯泼溅渲染器包（未安装时动态导入回退占位） */
declare module '@mkkelogg/gaussian-splats-3d' {
  const Viewer: any
  export default Viewer
  export { Viewer }
}

interface ImportMetaEnv {
  readonly VITE_API_BASE?: string
  readonly VITE_WS_BASE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
