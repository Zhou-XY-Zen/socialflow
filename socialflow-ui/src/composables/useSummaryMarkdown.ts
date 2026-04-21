/**
 * useSummaryMarkdown —— 把分析报告的 summaryMd 渲染成 HTML，
 * 并在挂载到 DOM 后把内嵌的 ```mermaid 代码块替换为 SVG。
 *
 * 背景：LLM 生成的 summaryMd 里会直接写 ```mermaid ... ``` 代码块
 * （如"典型数据流"小节里的 graph TD），若走默认 markdown-it 只会渲染成 <pre>，
 * 用户就看到一堆源码而不是流程图。本组合式封装：
 *   1) 覆盖 markdown-it 的 fence renderer，把 mermaid 块输出为占位 div
 *   2) summaryHtml 变化 → 下一 tick → 扫描占位 div 并调用 mermaid.render 替换
 *
 * 用法：
 *   const { summaryHtml, containerRef } = useSummaryMarkdown(() => current.value?.summaryMd)
 *   <div ref="containerRef" v-html="summaryHtml" />
 */
import { computed, nextTick, ref, watch, type Ref } from 'vue'
import MarkdownIt from 'markdown-it'
import mermaid from 'mermaid'
import './useSummaryMarkdown.css'

let mermaidInited = false
function initMermaidOnce() {
  if (mermaidInited) return
  mermaid.initialize({
    startOnLoad: false,
    theme: 'default',
    securityLevel: 'loose',
    flowchart: { useMaxWidth: true, htmlLabels: true, curve: 'basis' },
    sequence: { useMaxWidth: true, wrap: true },
  })
  mermaidInited = true
}

/** 处理含 Unicode 的 base64 编码 */
function utf8ToBase64(s: string): string {
  return btoa(unescape(encodeURIComponent(s)))
}
function base64ToUtf8(s: string): string {
  return decodeURIComponent(escape(atob(s)))
}

function buildMd(): MarkdownIt {
  const md = new MarkdownIt({ html: false, linkify: true, breaks: true })
  const defaultFence = md.renderer.rules.fence
  md.renderer.rules.fence = function (tokens, idx, options, env, self) {
    const token = tokens[idx]
    const info = (token.info || '').trim().toLowerCase()
    if (info === 'mermaid') {
      const encoded = utf8ToBase64(token.content)
      return `<div class="mermaid-inline" data-mermaid="${encoded}"><pre class="mermaid-fallback"><code>${md.utils.escapeHtml(token.content)}</code></pre></div>`
    }
    return defaultFence ? defaultFence(tokens, idx, options, env, self) : self.renderToken(tokens, idx, options)
  }
  return md
}

export function useSummaryMarkdown(source: () => string | undefined | null) {
  const md = buildMd()
  const containerRef = ref<HTMLElement>()

  const summaryHtml = computed(() => {
    const s = source()
    return s ? md.render(s) : ''
  })

  async function renderInline() {
    const host = containerRef.value
    if (!host) return
    const nodes = host.querySelectorAll<HTMLElement>('.mermaid-inline[data-mermaid]')
    if (nodes.length === 0) return
    initMermaidOnce()
    for (let i = 0; i < nodes.length; i++) {
      const el = nodes[i]
      const encoded = el.dataset.mermaid || ''
      el.removeAttribute('data-mermaid')  // 防止 watch 重跑时重复渲染
      try {
        const code = base64ToUtf8(encoded)
        const id = 'mm-inline-' + Date.now() + '-' + i
        const { svg } = await mermaid.render(id, code)
        el.innerHTML = svg
        el.classList.add('rendered')
      } catch (e: unknown) {
        const msg = e instanceof Error ? e.message : String(e)
        console.warn('[mermaid inline] render failed:', msg)
        el.classList.add('render-failed')
        const tip = document.createElement('div')
        tip.className = 'mermaid-inline-error'
        tip.textContent = '⚠️ 流程图渲染失败：' + msg
        el.prepend(tip)
      }
    }
  }

  watch(summaryHtml, async () => {
    await nextTick()
    await renderInline()
  })

  return { summaryHtml, containerRef: containerRef as Ref<HTMLElement | undefined>, renderInline }
}
