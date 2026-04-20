/**
 * 基于 html2pdf.js 的前端"所见即所得" PDF 导出。
 *
 * 为什么用前端生成而非服务端 PDFBox：
 *   - 服务端渲染要从零实现 CSS 引擎（Markdown 富文本、Element Plus 样式、语言占比条、
 *     Mermaid SVG 等），工作量巨大且无法与页面保持视觉一致
 *   - html2pdf = html2canvas（DOM→canvas）+ jsPDF（canvas→PDF），直接把用户看到的
 *     页面截下来做成 PDF，100% 视觉对齐
 *
 * 代价：
 *   - PDF 里的文字是图片（不可选中/搜索），但对"给人看的报告"场景可接受
 *   - 体积大于纯文本 PDF（一页 ~300KB~1MB）
 */
import html2pdf from 'html2pdf.js'

export interface ExportPdfOptions {
  /** 从克隆节点里移除的选择器，如按钮、折叠面板等不应出现在 PDF 里的元素 */
  excludeSelectors?: string[]
  /** PDF 页面边距（mm），默认 10 */
  marginMm?: number
  /** html2canvas 缩放倍率，默认 2（高分屏更清晰，但增大 PDF 体积） */
  scale?: number
}

/**
 * 把 DOM 节点导出为 PDF 文件并触发浏览器下载。
 *
 * 流程：
 *   1. 深拷贝源节点（避免动原页面）
 *   2. 按 excludeSelectors 移除不要的元素
 *   3. 挂到屏幕外做离屏渲染（html2canvas 需元素在 DOM 树中）
 *   4. html2pdf 生成并 save
 *   5. 清理克隆节点
 */
export async function exportPdfFromDom(
  source: HTMLElement,
  filename: string,
  options: ExportPdfOptions = {},
): Promise<void> {
  const { excludeSelectors = [], marginMm = 10, scale = 2 } = options

  // 克隆前先记录原 DOM 中所有 SVG/canvas 的真实尺寸。
  // 原因：SVG 的宽高经常来自父级 CSS（如 .mermaid-svg svg { max-width: 100% }），
  // 一旦克隆到屏幕外脱离父链路，getBoundingClientRect 可能为 0，
  // html2canvas 会给它建 0×0 canvas 然后 createPattern 抛
  //   "Failed to execute 'createPattern': canvas with width or height of 0"
  const srcSvgRects = Array.from(source.querySelectorAll('svg'))
    .map((el) => el.getBoundingClientRect())
  const srcCanvasRects = Array.from(source.querySelectorAll('canvas'))
    .map((el) => el.getBoundingClientRect())

  const clone = source.cloneNode(true) as HTMLElement
  excludeSelectors.forEach((sel) => {
    clone.querySelectorAll(sel).forEach((el) => el.remove())
  })

  // 按顺序把原 SVG 尺寸回写到克隆的 SVG，避免 0×0 导致 html2canvas 崩
  clone.querySelectorAll('svg').forEach((svg, i) => {
    const r = srcSvgRects[i]
    if (r && r.width > 0 && r.height > 0) {
      svg.setAttribute('width', String(Math.ceil(r.width)))
      svg.setAttribute('height', String(Math.ceil(r.height)))
      svg.style.width = `${Math.ceil(r.width)}px`
      svg.style.height = `${Math.ceil(r.height)}px`
    }
  })
  // canvas 同样处理（ScoreGauge 的 D3 圆环等）
  clone.querySelectorAll('canvas').forEach((c, i) => {
    const r = srcCanvasRects[i]
    if (r && r.width > 0 && r.height > 0) {
      c.width = Math.ceil(r.width)
      c.height = Math.ceil(r.height)
      c.style.width = `${Math.ceil(r.width)}px`
      c.style.height = `${Math.ceil(r.height)}px`
    }
  })

  // 离屏挂载，保留源宽度以避免响应式 CSS 切成窄屏版
  const width = source.getBoundingClientRect().width
  clone.style.position = 'fixed'
  clone.style.left = '0'
  clone.style.top = '-99999px'
  clone.style.width = `${width}px`
  clone.style.maxWidth = 'none'
  clone.style.background = '#ffffff'
  document.body.appendChild(clone)

  try {
    // html2pdf.js 的 d.ts 没声明 pagebreak 字段（运行时支持），用 as any 绕过类型检查
    const opts = {
      margin: marginMm,
      filename,
      image: { type: 'jpeg', quality: 0.95 },
      html2canvas: {
        scale,
        useCORS: true,
        backgroundColor: '#ffffff',
        windowWidth: width,
      },
      jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' },
      // avoid-all：尽量不在元素中间切页；css：尊重 page-break-* 样式
      pagebreak: { mode: ['avoid-all', 'css', 'legacy'] },
    } as any
    await html2pdf().from(clone).set(opts).save()
  } finally {
    if (clone.parentNode) clone.parentNode.removeChild(clone)
  }
}
