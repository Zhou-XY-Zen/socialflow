<!--
  MermaidViewer —— 把 Mermaid 渲染好的 SVG 字符串渲染出来，并提供
  滚轮缩放 / 拖拽 / 重置 / 全屏 四项交互能力。

  核心流程图节点多时（socialflow 本身就是 20+ 节点），默认视图里字会挤成一团。
  本组件用 @panzoom/panzoom（7KB）包装，鼠标滚轮缩放、按下拖拽、点击按钮重置，
  全屏模式用原生 Element.requestFullscreen。

  Props:
    svg: string   — useMermaid render() 出来的 svg HTML 字符串

  用法：<MermaidViewer :svg="mermaidSvg" />
-->
<script setup lang="ts">
import { ref, watch, onBeforeUnmount, nextTick } from 'vue'
import panzoom, { type PanZoom } from 'panzoom'

const props = defineProps<{ svg: string }>()

const canvas = ref<HTMLElement>()
const viewer = ref<HTMLElement>()
const isFullscreen = ref(false)
let pz: PanZoom | null = null

async function mountPanzoom() {
  pz?.dispose()
  pz = null
  await nextTick()
  const host = canvas.value
  if (!host) return
  const svgEl = host.querySelector<SVGElement>('svg')
  if (!svgEl) return
  // 让 svg 不再被自身 width/height 属性限制 —— 由 panzoom 的 transform 控制大小
  svgEl.removeAttribute('width')
  svgEl.removeAttribute('height')
  svgEl.style.maxWidth = 'none'
  svgEl.style.maxHeight = 'none'
  svgEl.style.cursor = 'grab'
  pz = panzoom(svgEl, {
    maxZoom: 8,
    minZoom: 0.2,
    smoothScroll: false,
    bounds: true,
    boundsPadding: 0.2,
    zoomDoubleClickSpeed: 1,  // 禁用双击缩放（避免点错），双击只做手动判定
  })
}

watch(() => props.svg, mountPanzoom, { immediate: true })

function zoomIn() { pz?.smoothZoom(0, 0, 1.4) }
function zoomOut() { pz?.smoothZoom(0, 0, 0.7) }
function reset() {
  if (!pz) return
  pz.moveTo(0, 0)
  pz.zoomAbs(0, 0, 1)
}
async function toggleFullscreen() {
  const el = viewer.value
  if (!el) return
  if (!document.fullscreenElement) {
    await el.requestFullscreen()
    isFullscreen.value = true
    // 全屏后容器变大，重新居中让用户看到整图
    setTimeout(() => reset(), 100)
  } else {
    await document.exitFullscreen()
    isFullscreen.value = false
  }
}

function onFullscreenChange() {
  isFullscreen.value = !!document.fullscreenElement
}
document.addEventListener('fullscreenchange', onFullscreenChange)
onBeforeUnmount(() => {
  pz?.dispose()
  document.removeEventListener('fullscreenchange', onFullscreenChange)
})
</script>

<template>
  <div ref="viewer" class="mm-viewer" :class="{ 'is-fullscreen': isFullscreen }">
    <div class="mm-toolbar">
      <span class="mm-hint">🖱 滚轮缩放 · 拖拽平移</span>
      <div class="mm-btns">
        <button class="mm-btn" title="放大" @click="zoomIn">🔍+</button>
        <button class="mm-btn" title="缩小" @click="zoomOut">🔍−</button>
        <button class="mm-btn" title="重置视图" @click="reset">⟲ 重置</button>
        <button class="mm-btn mm-btn-primary" title="全屏查看" @click="toggleFullscreen">
          {{ isFullscreen ? '✕ 退出全屏' : '⤢ 全屏' }}
        </button>
      </div>
    </div>
    <div ref="canvas" class="mm-canvas" v-html="svg" />
  </div>
</template>

<style scoped>
.mm-viewer {
  position: relative;
  display: flex;
  flex-direction: column;
  background: #fafafa;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
}

.mm-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}

.mm-hint {
  color: #6b7280;
  font-size: 12px;
}

.mm-btns {
  display: flex;
  gap: 6px;
}

.mm-btn {
  padding: 4px 10px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  color: #374151;
  transition: all 0.15s;
  font-variant-numeric: tabular-nums;
}
.mm-btn:hover {
  background: #f3f4f6;
  border-color: #d1d5db;
}
.mm-btn-primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  border-color: transparent;
}
.mm-btn-primary:hover {
  opacity: 0.9;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.mm-canvas {
  flex: 1;
  position: relative;
  overflow: hidden;
  height: 560px;
  background:
    linear-gradient(#e5e7eb 1px, transparent 1px) 0 0 / 24px 24px,
    linear-gradient(90deg, #e5e7eb 1px, transparent 1px) 0 0 / 24px 24px,
    #fafafa;
}

.mm-canvas :deep(svg) {
  /* panzoom 会给 svg 加 transform，不要再限制它的尺寸 */
  display: block;
  user-select: none;
}

/* 全屏模式下工具条更显眼，画布铺满 */
.mm-viewer.is-fullscreen {
  background: #fff;
}
.mm-viewer.is-fullscreen .mm-canvas {
  height: calc(100vh - 48px);
}
</style>
