<!--
  PreferencesPanel —— 代码分析本地偏好（localStorage）
  从原 src/views/code-analysis/Settings.vue 合并过来，保留原功能（Git 排除目录 + 通知）。
  LLM provider/temperature 已迁移到 CodeAnalysisModelPanel，这里去掉。
-->
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const KEY = 'socialflow.code-analysis.settings'

const settings = ref({
  excludeDirs: 'node_modules,target,dist,.git,.idea,.vscode,build,out',
  notifyOnComplete: true,
})

onMounted(() => {
  try {
    const raw = localStorage.getItem(KEY)
    if (raw) Object.assign(settings.value, JSON.parse(raw))
  } catch { /* ignore */ }
})

function save() {
  localStorage.setItem(KEY, JSON.stringify(settings.value))
  ElMessage.success('偏好已保存（本地）')
}

function reset() {
  localStorage.removeItem(KEY)
  location.reload()
}
</script>

<template>
  <div class="panel">
    <header class="panel-header">
      <h2>🎛️ 代码分析 · 偏好</h2>
      <p class="panel-desc">保存在当前浏览器本地；影响分析表单的默认值</p>
    </header>

    <el-form label-width="140px" label-position="left">
      <el-divider content-position="left">Git 参数</el-divider>
      <el-form-item label="默认排除目录">
        <el-input v-model="settings.excludeDirs" type="textarea" :rows="2"
                  placeholder="逗号分隔，例如 node_modules,target,dist" />
        <div class="sub-hint">扫描时跳过的目录（依赖包、构建产物等）</div>
      </el-form-item>

      <el-divider content-position="left">通知</el-divider>
      <el-form-item label="分析完成通知">
        <el-switch v-model="settings.notifyOnComplete" />
        <div class="sub-hint">完成后在浏览器内弹一个 toast</div>
      </el-form-item>

      <el-divider />
      <el-form-item>
        <el-button type="primary" @click="save">💾 保存</el-button>
        <el-button @click="reset">↺ 恢复默认</el-button>
      </el-form-item>
    </el-form>

    <div class="info-card">
      <div class="info-title">📊 关于代码分析模块</div>
      <ul class="about">
        <li>分析基于 <strong>阿里巴巴 Java 开发手册（黄山版 1.7.1）</strong>的 <strong>321 条</strong>规约</li>
        <li>审查策略：<strong>Map-Reduce 全量扫描</strong>（项目概览按模块、提交审查按文件），<strong>不做 diff 截断</strong></li>
        <li>支持语言：Java / Kotlin / TypeScript / Python / SQL / Go / Rust（前端 SPA 源码已屏蔽）</li>
        <li>Git 操作：Eclipse JGit（纯 Java，无需系统 git）</li>
        <li>误判防护：6 层机制（codeSnippet 校验 / ruleRef 白名单 / 行号校验 / self-check / 反馈闭环 / PMD 双引擎）</li>
        <li>每次分析独立临时克隆，完成后自动清理</li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.panel { display: flex; flex-direction: column; gap: 16px; }
.panel-header h2 { margin: 0 0 4px; font-size: 20px; color: #111827; }
.panel-desc { margin: 0; color: #6b7280; font-size: 13px; }
.sub-hint { color: #9ca3af; font-size: 12px; margin-top: 4px; }

.info-card { background: #f9fafb; border: 1px solid #e5e7eb; padding: 14px 18px; border-radius: 8px; }
.info-title { font-size: 13px; color: #374151; font-weight: 600; margin-bottom: 8px; }
.about { margin: 0; padding-left: 20px; color: #4b5563; line-height: 1.9; font-size: 13px; }
</style>
