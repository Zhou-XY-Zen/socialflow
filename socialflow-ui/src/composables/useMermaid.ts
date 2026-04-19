/**
 * useMermaid —— 把 Mermaid 源码文本渲染成内联 SVG
 *
 * 用法：
 *   const { svg, error, render } = useMermaid()
 *   await render(mermaidCode)
 *   <div v-html="svg" />
 */
import { ref } from 'vue'
import mermaid from 'mermaid'

let inited = false

function initOnce() {
  if (inited) return
  mermaid.initialize({
    startOnLoad: false,
    theme: 'default',
    securityLevel: 'loose',
    flowchart: { useMaxWidth: true, htmlLabels: true, curve: 'basis' },
    sequence:  { useMaxWidth: true, wrap: true },
    themeVariables: {
      primaryColor: '#ede9fe',
      primaryTextColor: '#1f2937',
      primaryBorderColor: '#7c3aed',
      lineColor: '#6b7280',
      secondaryColor: '#e0e7ff',
      tertiaryColor: '#f3e8ff',
    },
  })
  inited = true
}

export function useMermaid() {
  const svg = ref('')
  const error = ref<string>('')
  const rendering = ref(false)

  async function render(code: string | null | undefined) {
    error.value = ''
    svg.value = ''
    if (!code || !code.trim()) return
    initOnce()
    rendering.value = true
    try {
      const id = 'mm-' + Math.random().toString(36).slice(2, 10)
      const cleaned = stripFence(code.trim())
      const { svg: rendered } = await mermaid.render(id, cleaned)
      svg.value = rendered
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e)
      error.value = msg
      console.warn('[mermaid] render failed:', msg)
    } finally {
      rendering.value = false
    }
  }

  return { svg, error, rendering, render }
}

/** LLM 经常把 mermaid 代码放在 ```mermaid ... ``` 里，去掉围栏 */
function stripFence(s: string): string {
  if (s.startsWith('```')) {
    const firstNl = s.indexOf('\n')
    const last = s.lastIndexOf('```')
    if (firstNl > 0 && last > firstNl) return s.slice(firstNl + 1, last).trim()
  }
  return s
}
