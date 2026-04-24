/**
 * mermaidConfig —— 共用 Mermaid 初始化
 *
 * 两处渲染 mermaid 的地方（独立 mermaidCode 的核心架构图 + summaryMd 内嵌的 graph TD）
 * 之前各自 initialize 一次，配置几乎一致。这里统一。
 *
 * useMaxWidth 由调用方决定（MermaidViewer 要 false 让 panzoom 接管尺寸；
 * summary 内嵌简易渲染要 true 自适应容器）—— initialize 本身不管这个，
 * 每次 mermaid.render 时根据调用方传入的 code 自然生效。
 */
import mermaid from 'mermaid'

let inited = false

export function initMermaidOnce(useMaxWidth = false) {
  if (inited) return
  mermaid.initialize({
    startOnLoad: false,
    theme: 'default',
    securityLevel: 'loose',
    flowchart: { useMaxWidth, htmlLabels: true, curve: 'basis' },
    sequence:  { useMaxWidth, wrap: true },
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
