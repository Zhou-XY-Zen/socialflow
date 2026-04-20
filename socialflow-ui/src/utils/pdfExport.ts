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

  const clone = source.cloneNode(true) as HTMLElement
  excludeSelectors.forEach((sel) => {
    clone.querySelectorAll(sel).forEach((el) => el.remove())
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
