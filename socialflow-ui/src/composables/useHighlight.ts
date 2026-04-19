/**
 * useHighlight —— 用 highlight.js 对任意代码片段做语法高亮
 *
 * 按需注册常用语言，避免 highlight.js 全量包（800KB+）打包进 bundle。
 */
import hljs from 'highlight.js/lib/core'

// 注册代码审查最常见的语言
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import python from 'highlight.js/lib/languages/python'
import sql from 'highlight.js/lib/languages/sql'
import xml from 'highlight.js/lib/languages/xml'
import json from 'highlight.js/lib/languages/json'
import yaml from 'highlight.js/lib/languages/yaml'
import bash from 'highlight.js/lib/languages/bash'
import go from 'highlight.js/lib/languages/go'
import rust from 'highlight.js/lib/languages/rust'
import css from 'highlight.js/lib/languages/css'

// 引入暗色主题样式（atom-one-dark 配色与项目 dark 代码块一致）
import 'highlight.js/styles/atom-one-dark.css'

hljs.registerLanguage('java', java)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('js', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('ts', typescript)
hljs.registerLanguage('python', python)
hljs.registerLanguage('py', python)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('vue', xml)
hljs.registerLanguage('json', json)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('yml', yaml)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('shell', bash)
hljs.registerLanguage('sh', bash)
hljs.registerLanguage('go', go)
hljs.registerLanguage('rust', rust)
hljs.registerLanguage('rs', rust)
hljs.registerLanguage('css', css)

/** 根据文件扩展名猜语言 */
export function languageFromFile(file?: string | null): string | undefined {
  if (!file) return undefined
  const m = file.toLowerCase().match(/\.([a-z]+)$/)
  if (!m) return undefined
  const ext = m[1]
  const map: Record<string, string> = {
    java: 'java', kt: 'java',
    ts: 'typescript', tsx: 'typescript',
    js: 'javascript', jsx: 'javascript',
    vue: 'xml', html: 'xml', htm: 'xml',
    py: 'python',
    sql: 'sql',
    xml: 'xml',
    json: 'json',
    yaml: 'yaml', yml: 'yaml',
    sh: 'bash', bash: 'bash',
    go: 'go',
    rs: 'rust',
    css: 'css', scss: 'css', less: 'css',
  }
  return map[ext]
}

/** 高亮一段代码，返回 HTML 字符串。语言识别失败时走 auto。 */
export function highlight(code: string, language?: string): string {
  if (!code) return ''
  try {
    if (language && hljs.getLanguage(language)) {
      return hljs.highlight(code, { language, ignoreIllegals: true }).value
    }
    return hljs.highlightAuto(code).value
  } catch {
    return escapeHtml(code)
  }
}

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]!))
}
