/**
 * 工具注册表
 * -------------------------------------------------
 * 容器壳（Workspace.vue）通过这里发现所有可用工具。
 * 工具之间互不相关，壳对工具内部一无所知。
 *
 * 新增工具的步骤：
 *   1. 在 `src/views/tools/` 下新建工具组件（自包含布局/状态/样式）
 *   2. 在下方 `tools` 数组追加一项
 *   3. 完成 —— 不需要改 Workspace.vue
 */
import { markRaw, type Component } from 'vue'
import CopywritingPanel from './CopywritingPanel.vue'

export interface Tool {
  /** 唯一标识；URL query 值、tab name、keep-alive key 都用它 */
  key: string
  /** Tab 显示文字 */
  label: string
  /** 工具主组件（必须经 markRaw 包裹，避免 Vue 把组件做深度响应式） */
  component: Component
}

export const tools: Tool[] = [
  {
    key: 'copywriting',
    label: '文案创作',
    component: markRaw(CopywritingPanel),
  },
  // 新工具注册在这里，无需改 Workspace.vue
]
