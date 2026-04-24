/**
 * 通用格式化工具 —— 代码分析模块（和其他任何地方）的通用输出格式化。
 *
 * 之前 Dashboard/AnalysisResult/History/CommitReview/ProjectOverview 都各自实现了
 * 一份 fmtTokens/fmtDuration/fmtLatency/fmtTime，完全重复。这里抽出来统一。
 */

/** Token 数字：123 / 12.3K / 1.23M
 *  @param zeroAs 0 的显示（默认 '-'；Dashboard/ProjectOverview 的累计场景传 '0' 更合理）*/
export function fmtTokens(n?: number | null, zeroAs: string = '-'): string {
  if (n == null) return '-'
  if (n === 0) return zeroAs
  if (n < 1000) return String(n)
  if (n < 1_000_000) return (n / 1000).toFixed(1) + 'K'
  return (n / 1_000_000).toFixed(2) + 'M'
}

/** LLM 单次调用延迟：789 ms / 12.3 s */
export function fmtLatency(ms?: number | null): string {
  if (ms == null) return '-'
  if (ms < 1000) return ms + ' ms'
  return (ms / 1000).toFixed(1) + ' s'
}

/** 分析总耗时（偏长）：1.2s / 45s / 3m 25s / 1h 5m */
export function fmtDuration(ms?: number | null): string {
  if (ms == null || ms <= 0) return '-'
  if (ms < 1000) return ms + 'ms'
  const sec = Math.round(ms / 1000)
  if (sec < 60) return sec + 's'
  const min = Math.floor(sec / 60)
  const rsec = sec % 60
  if (min < 60) return rsec > 0 ? `${min}m ${rsec}s` : `${min}m`
  const hr = Math.floor(min / 60)
  const rmin = min % 60
  return rmin > 0 ? `${hr}h ${rmin}m` : `${hr}h`
}

/** 耗时健康色（对标代码分析预期 6-8 分钟）：≤8m 绿 / ≤20m 黄 / >20m 红 */
export function durationColor(ms?: number | null): string {
  if (ms == null || ms <= 0) return '#9ca3af'
  if (ms <= 8 * 60_000) return '#059669'
  if (ms <= 20 * 60_000) return '#b45309'
  return '#b91c1c'
}

/** 本地化时间戳 */
export function fmtTime(s?: string | null): string {
  if (!s) return ''
  return new Date(s).toLocaleString('zh-CN')
}
