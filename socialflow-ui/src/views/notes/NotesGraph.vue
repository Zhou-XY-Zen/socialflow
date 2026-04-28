<!--
  NotesGraph.vue —— 知识图谱视图
   - 节点：所有出现在 link 表里的笔记
   - 边：explicit 实线、semantic 虚线（P3 才会有）
   - 简易 Verlet 力导（自实现，无 g6 依赖）：库仑排斥 + 胡克吸引 + 中心引力
   - 拖拽节点 / 滚轮缩放 / 点击跳转笔记
-->

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { noteApi } from '@/api/note'
import type { NoteLinkVO } from '@/types/api'

interface Node {
  id: string
  title: string
  x: number; y: number
  vx: number; vy: number
  fx?: number | null      // 拖拽固定坐标
  fy?: number | null
  degree: number
}
interface Edge { src: string; dst: string; type: string }

const router = useRouter()
const loading = ref(false)
const nodes = ref<Node[]>([])
const edges = ref<Edge[]>([])
const tick = ref(0)
const transform = ref({ x: 0, y: 0, k: 1 })
const W = 1100, H = 640
const dragging = ref<Node | null>(null)
let raf: number | null = null

async function load() {
  loading.value = true
  try {
    const links = await noteApi.graphEdges()
    buildGraph(links)
    if (nodes.value.length) startLayout()
  } finally { loading.value = false }
}

function buildGraph(links: NoteLinkVO[]) {
  const map = new Map<string, Node>()
  const addNode = (id: string, title?: string) => {
    if (!map.has(id)) {
      map.set(id, {
        id, title: title ?? `#${id}`,
        x: W / 2 + (Math.random() - .5) * 300,
        y: H / 2 + (Math.random() - .5) * 200,
        vx: 0, vy: 0, degree: 0,
      })
    } else if (title && (map.get(id)!.title.startsWith('#'))) {
      map.get(id)!.title = title
    }
  }
  edges.value = []
  for (const l of links) {
    addNode(l.srcNoteId, l.srcTitle)
    addNode(l.dstNoteId, l.dstTitle)
    edges.value.push({ src: l.srcNoteId, dst: l.dstNoteId, type: l.linkType })
    map.get(l.srcNoteId)!.degree++
    map.get(l.dstNoteId)!.degree++
  }
  nodes.value = Array.from(map.values())
}

/* ===== 力导布局（简易 Verlet）===== */

function startLayout() {
  if (raf != null) return
  let iters = 0
  const step = () => {
    iters++; tick.value++
    applyForces()
    if (iters < 600) raf = requestAnimationFrame(step)
    else raf = null
  }
  raf = requestAnimationFrame(step)
}

function applyForces() {
  const ns = nodes.value
  const REPULSE = 1400      // 库仑系数
  const SPRING_LEN = 80
  const SPRING_K = 0.03
  const CENTER_K = 0.001
  const FRICTION = 0.86

  // 中心引力
  for (const n of ns) {
    n.vx += (W / 2 - n.x) * CENTER_K
    n.vy += (H / 2 - n.y) * CENTER_K
  }
  // 节点两两排斥
  for (let i = 0; i < ns.length; i++) {
    for (let j = i + 1; j < ns.length; j++) {
      const a = ns[i], b = ns[j]
      let dx = b.x - a.x, dy = b.y - a.y
      let d2 = dx * dx + dy * dy
      if (d2 < 1) { d2 = 1; dx = Math.random(); dy = Math.random() }
      const force = REPULSE / d2
      const d = Math.sqrt(d2)
      const fx = (dx / d) * force, fy = (dy / d) * force
      a.vx -= fx; a.vy -= fy
      b.vx += fx; b.vy += fy
    }
  }
  // 边的弹簧吸引
  const byId = new Map(ns.map(n => [n.id, n]))
  for (const e of edges.value) {
    const a = byId.get(e.src), b = byId.get(e.dst)
    if (!a || !b) continue
    const dx = b.x - a.x, dy = b.y - a.y
    const d = Math.sqrt(dx * dx + dy * dy) || 1
    const force = (d - SPRING_LEN) * SPRING_K
    const fx = (dx / d) * force, fy = (dy / d) * force
    a.vx += fx; a.vy += fy
    b.vx -= fx; b.vy -= fy
  }
  // 应用速度 + 摩擦
  for (const n of ns) {
    if (n.fx != null) { n.x = n.fx; n.vx = 0 } else { n.vx *= FRICTION; n.x += n.vx }
    if (n.fy != null) { n.y = n.fy; n.vy = 0 } else { n.vy *= FRICTION; n.y += n.vy }
  }
}

/* ===== 交互：拖拽 / 缩放 / 点击 ===== */

function onMouseDown(n: Node, e: MouseEvent) {
  dragging.value = n
  n.fx = n.x; n.fy = n.y
  startLayout()
  e.preventDefault()
}
function onMouseMove(e: MouseEvent) {
  if (!dragging.value) return
  const svg = (e.currentTarget as SVGSVGElement).getBoundingClientRect()
  const x = (e.clientX - svg.left - transform.value.x) / transform.value.k
  const y = (e.clientY - svg.top - transform.value.y) / transform.value.k
  dragging.value.fx = x
  dragging.value.fy = y
}
function onMouseUp() {
  if (dragging.value) {
    dragging.value.fx = null; dragging.value.fy = null
    dragging.value = null
  }
}
function onWheel(e: WheelEvent) {
  e.preventDefault()
  const delta = -e.deltaY / 500
  const k = Math.min(3, Math.max(0.3, transform.value.k * (1 + delta)))
  transform.value.k = k
}
function clickNode(n: Node) {
  if (dragging.value) return
  router.push({ name: 'notes-edit', params: { id: n.id } })
}

const nodeColor = (n: Node) => {
  if (n.degree >= 5) return '#ef4444'
  if (n.degree >= 3) return '#f59e0b'
  if (n.degree >= 1) return '#3b82f6'
  return '#9ca3af'
}
const radius = (n: Node) => 6 + Math.min(14, n.degree * 1.5)

const empty = computed(() => !loading.value && nodes.value.length === 0)

/* 模板里渲染边时按 id 拿坐标用 —— 避免每帧 nodes.find O(N) */
const nodeById = computed(() => {
  const m = new Map<string, Node>()
  for (const n of nodes.value) m.set(n.id, n)
  return m
})

onMounted(load)
onBeforeUnmount(() => { if (raf != null) cancelAnimationFrame(raf) })
</script>

<template>
  <div class="graph-page">
    <PageHeader title="知识图谱"
                subtitle="节点 = 笔记 · 边 = [[双向链接]] · 节点越大表示被引用越多"
                icon="Share">
      <template #actions>
        <el-button :icon="'Refresh'" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <EmptyState v-if="empty"
                title="还没有图可显示"
                description="在笔记里用 [[标题]] 引用其他笔记，会自动建立边" />

    <svg v-else class="canvas" :width="W" :height="H"
         @mousemove="onMouseMove" @mouseup="onMouseUp" @mouseleave="onMouseUp" @wheel="onWheel">
      <g :transform="`translate(${transform.x}, ${transform.y}) scale(${transform.k})`">
        <line v-for="(e, i) in edges" :key="'e'+i+'-'+tick"
              :x1="nodeById.get(e.src)?.x"
              :y1="nodeById.get(e.src)?.y"
              :x2="nodeById.get(e.dst)?.x"
              :y2="nodeById.get(e.dst)?.y"
              stroke="#cbd5e1" :stroke-dasharray="e.type === 'semantic' ? '4 3' : ''" stroke-width="1" />
        <g v-for="n in nodes" :key="n.id">
          <circle :cx="n.x" :cy="n.y" :r="radius(n)" :fill="nodeColor(n)" stroke="#fff" stroke-width="2"
                  style="cursor:pointer"
                  @mousedown="onMouseDown(n, $event)" @click="clickNode(n)" />
          <text :x="n.x" :y="n.y - radius(n) - 4" font-size="11" text-anchor="middle"
                fill="#374151" pointer-events="none">{{ n.title }}</text>
        </g>
      </g>
    </svg>

    <div v-if="!empty" class="legend">
      <span>节点数 {{ nodes.length }} · 边数 {{ edges.length }}</span>
      <span style="margin-left:14px">
        <span class="dot" style="background:#9ca3af"></span> 0 引用
        <span class="dot" style="background:#3b82f6;margin-left:8px"></span> 1-2
        <span class="dot" style="background:#f59e0b;margin-left:8px"></span> 3-4
        <span class="dot" style="background:#ef4444;margin-left:8px"></span> 5+
      </span>
      <span style="margin-left:14px;color:#9ca3af">拖拽节点 · 滚轮缩放 · 点击跳转</span>
    </div>
  </div>
</template>

<style scoped>
.graph-page { padding: 16px; }
.canvas { width: 100%; max-width: 1100px; height: auto; background: #fafafa;
          border: 1px solid #e5e7eb; border-radius: 12px; user-select: none; cursor: grab; }
.legend { margin-top: 8px; font-size: 12px; color: #6b7280; display: flex; align-items: center; flex-wrap: wrap; }
.dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 4px;
       vertical-align: middle; }
</style>
