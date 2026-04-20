<!--
  Rules.vue —— 阿里巴巴 Java 开发手册（黄山版）规约库
  Wave 7：从 API 加载（rule_library 表），支持启停 / 自定义规约
-->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { codeAnalysisApi } from '@/api/codeAnalysis'
import type { RuleLibraryItem } from '@/types/codeAnalysis'

// 大类元信息（图标 + 主题色）
const TOP_META: Record<string, { icon: string; color: string }> = {
  编程规约:    { icon: '📝', color: '#6d28d9' },
  异常日志:    { icon: '⚠️', color: '#dc2626' },
  单元测试:    { icon: '🧪', color: '#0891b2' },
  安全规约:    { icon: '🛡️', color: '#dc2626' },
  MySQL数据库: { icon: '🗄️', color: '#0369a1' },
  工程结构:    { icon: '🏗️', color: '#7c3aed' },
  设计规约:    { icon: '🎯', color: '#059669' },
}

interface TopCategory {
  name: string
  icon: string
  color: string
  count: number
  children: Array<{ name: string; fullName: string; count: number }>
}

const rules = ref<RuleLibraryItem[]>([])
const loading = ref(false)

async function loadRules() {
  loading.value = true
  try {
    rules.value = await codeAnalysisApi.listRules({})
  } catch (e: any) {
    ElMessage.error('加载规约失败：' + (e?.message || e))
  } finally {
    loading.value = false
  }
}

onMounted(loadRules)

// 计算树形结构
const topCategories = computed<TopCategory[]>(() => {
  const groups = new Map<string, TopCategory>()
  rules.value.forEach(r => {
    const top = r.topCategory
    const sub = r.subCategory || top
    if (!groups.has(top)) {
      const meta = TOP_META[top] || { icon: '📎', color: '#6b7280' }
      groups.set(top, { name: top, icon: meta.icon, color: meta.color, count: 0, children: [] })
    }
    const g = groups.get(top)!
    g.count++
    const fullName = r.subCategory ? `${top}-${r.subCategory}` : top
    const child = g.children.find(c => c.fullName === fullName)
    if (child) child.count++
    else g.children.push({ name: sub, fullName, count: 1 })
  })
  return Array.from(groups.values())
})

const currentFilter = ref<{ type: 'all' | 'top' | 'sub' | 'level' | 'disabled' | 'custom'; value?: string }>({ type: 'all' })
const searchKw = ref('')
const levelFilter = ref<'' | 'MANDATORY' | 'RECOMMENDED' | 'REFERENCE'>('')
const selectedRule = ref<RuleLibraryItem | null>(null)
const expandedTops = ref<Set<string>>(new Set(['编程规约']))

function toggleTop(name: string) {
  if (expandedTops.value.has(name)) expandedTops.value.delete(name)
  else expandedTops.value.add(name)
}

function fullNameOf(r: RuleLibraryItem) {
  return r.subCategory ? `${r.topCategory}-${r.subCategory}` : r.topCategory
}

const filteredRules = computed(() => {
  let r = rules.value
  if (currentFilter.value.type === 'top') {
    r = r.filter(x => x.topCategory === currentFilter.value.value)
  } else if (currentFilter.value.type === 'sub') {
    r = r.filter(x => fullNameOf(x) === currentFilter.value.value)
  } else if (currentFilter.value.type === 'level') {
    r = r.filter(x => x.level === currentFilter.value.value)
  } else if (currentFilter.value.type === 'disabled') {
    r = r.filter(x => x.enabled === 0)
  } else if (currentFilter.value.type === 'custom') {
    r = r.filter(x => x.isCustom === 1)
  }
  if (levelFilter.value) r = r.filter(x => x.level === levelFilter.value)
  if (searchKw.value) {
    const q = searchKw.value.toLowerCase()
    r = r.filter(x =>
      x.title.toLowerCase().includes(q) ||
      x.code.includes(q) ||
      (x.body || '').toLowerCase().includes(q) ||
      x.topCategory.toLowerCase().includes(q) ||
      (x.subCategory || '').toLowerCase().includes(q))
  }
  return r
})

const levelMeta = {
  MANDATORY:   { label: '强制', color: '#ef4444', bg: '#fef2f2' },
  RECOMMENDED: { label: '推荐', color: '#f59e0b', bg: '#fffbeb' },
  REFERENCE:   { label: '参考', color: '#3b82f6', bg: '#eff6ff' },
}

const levelStats = computed(() => {
  const r = filteredRules.value
  return {
    total: r.length,
    mandatory: r.filter(x => x.level === 'MANDATORY').length,
    recommended: r.filter(x => x.level === 'RECOMMENDED').length,
    reference: r.filter(x => x.level === 'REFERENCE').length,
  }
})

const globalStats = computed(() => ({
  total: rules.value.length,
  enabled: rules.value.filter(r => r.enabled === 1).length,
  disabled: rules.value.filter(r => r.enabled === 0).length,
  custom: rules.value.filter(r => r.isCustom === 1).length,
}))

function setFilter(type: typeof currentFilter.value.type, value?: string) {
  currentFilter.value = { type, value }
}

function currentTitle() {
  if (currentFilter.value.type === 'all') return '全部规约'
  if (currentFilter.value.type === 'level') {
    return `【${levelMeta[currentFilter.value.value as keyof typeof levelMeta].label}】规约`
  }
  if (currentFilter.value.type === 'disabled') return '已禁用规约'
  if (currentFilter.value.type === 'custom') return '自定义规约'
  return currentFilter.value.value || '全部规约'
}

// 启停切换
async function toggleEnabled(r: RuleLibraryItem, evt?: MouseEvent) {
  if (evt) evt.stopPropagation()
  const next = r.enabled === 1 ? 0 : 1
  try {
    await codeAnalysisApi.toggleRuleEnabled(r.id, next)
    r.enabled = next
    ElMessage.success(next === 1 ? `已启用 ${r.code}` : `已禁用 ${r.code}（审查时不再触发）`)
  } catch (e: any) {
    ElMessage.error('切换失败：' + (e?.message || e))
  }
}

// 删除自定义规约
async function deleteCustom(r: RuleLibraryItem) {
  await ElMessageBox.confirm(`确定删除自定义规约 ${r.code} ${r.title}？`, '警告', { type: 'warning' })
  try {
    await codeAnalysisApi.deleteRule(r.id)
    ElMessage.success('已删除')
    selectedRule.value = null
    loadRules()
  } catch (e: any) {
    ElMessage.error('删除失败：' + (e?.message || e))
  }
}
</script>

<template>
  <div class="rules-page" v-loading="loading">
    <!-- 侧边栏 -->
    <aside class="side">
      <div class="side-header">
        <div class="side-title">📚 阿里开发手册</div>
        <div class="side-sub">
          黄山版 · {{ globalStats.total }} 条规约
          <span v-if="globalStats.disabled > 0">（{{ globalStats.disabled }} 禁用）</span>
        </div>
      </div>

      <!-- 一键快捷 -->
      <div class="quick-filters">
        <div class="qf-item" :class="{ on: currentFilter.type === 'all' }" @click="setFilter('all')">
          <span>📋 全部规约</span>
          <span class="qf-count">{{ globalStats.total }}</span>
        </div>
        <div class="qf-item qf-red" :class="{ on: currentFilter.type === 'level' && currentFilter.value === 'MANDATORY' }"
             @click="setFilter('level', 'MANDATORY')">
          <span>🔴 强制</span>
          <span class="qf-count">{{ rules.filter(r => r.level === 'MANDATORY').length }}</span>
        </div>
        <div class="qf-item qf-yellow" :class="{ on: currentFilter.type === 'level' && currentFilter.value === 'RECOMMENDED' }"
             @click="setFilter('level', 'RECOMMENDED')">
          <span>🟡 推荐</span>
          <span class="qf-count">{{ rules.filter(r => r.level === 'RECOMMENDED').length }}</span>
        </div>
        <div class="qf-item qf-blue" :class="{ on: currentFilter.type === 'level' && currentFilter.value === 'REFERENCE' }"
             @click="setFilter('level', 'REFERENCE')">
          <span>🔵 参考</span>
          <span class="qf-count">{{ rules.filter(r => r.level === 'REFERENCE').length }}</span>
        </div>
        <div v-if="globalStats.disabled > 0" class="qf-item qf-gray" :class="{ on: currentFilter.type === 'disabled' }"
             @click="setFilter('disabled')">
          <span>⏸️ 已禁用</span>
          <span class="qf-count">{{ globalStats.disabled }}</span>
        </div>
        <div v-if="globalStats.custom > 0" class="qf-item qf-purple" :class="{ on: currentFilter.type === 'custom' }"
             @click="setFilter('custom')">
          <span>✨ 自定义</span>
          <span class="qf-count">{{ globalStats.custom }}</span>
        </div>
      </div>

      <div class="cat-divider">按类别</div>

      <!-- 树形分类 -->
      <div class="cat-tree">
        <div v-for="top in topCategories" :key="top.name" class="cat-group">
          <div class="cat-top" :class="{ on: currentFilter.type === 'top' && currentFilter.value === top.name }"
               @click="setFilter('top', top.name); toggleTop(top.name)">
            <span class="cat-arrow" :class="{ rotated: expandedTops.has(top.name) }">▶</span>
            <span class="cat-icon">{{ top.icon }}</span>
            <span class="cat-name">{{ top.name }}</span>
            <span class="cat-count">{{ top.count }}</span>
          </div>
          <div v-show="expandedTops.has(top.name)" class="cat-children">
            <div v-for="c in top.children" :key="c.fullName" class="cat-child"
                 :class="{ on: currentFilter.type === 'sub' && currentFilter.value === c.fullName }"
                 @click="setFilter('sub', c.fullName)">
              <span class="cat-child-name">{{ c.name }}</span>
              <span class="cat-count">{{ c.count }}</span>
            </div>
          </div>
        </div>
      </div>
    </aside>

    <!-- 主区域 -->
    <main class="main">
      <!-- 顶部工具条 -->
      <div class="toolbar">
        <div class="page-header">
          <div class="page-title">{{ currentTitle() }}</div>
          <div class="page-sub">
            {{ levelStats.total }} 条
            <span v-if="levelStats.mandatory > 0" class="stat-pill red">强制 {{ levelStats.mandatory }}</span>
            <span v-if="levelStats.recommended > 0" class="stat-pill yellow">推荐 {{ levelStats.recommended }}</span>
            <span v-if="levelStats.reference > 0" class="stat-pill blue">参考 {{ levelStats.reference }}</span>
          </div>
        </div>
        <el-input v-model="searchKw" placeholder="搜索条款编号 / 标题 / 内容..." clearable
                  class="search-input" :prefix-icon="'Search'" />
      </div>

      <!-- 规约卡片网格 -->
      <div class="rules-grid">
        <div v-for="r in filteredRules" :key="r.id"
             class="rule-card" :class="[`lv-${r.level.toLowerCase()}`, { disabled: r.enabled === 0 }]"
             :data-active="selectedRule?.id === r.id"
             @click="selectedRule = r">
          <div class="rule-top">
            <span class="rule-code">{{ r.code }}</span>
            <div class="rule-top-right">
              <span v-if="r.isCustom === 1" class="badge-custom">自定义</span>
              <span class="rule-level"
                    :style="{ background: levelMeta[r.level].bg, color: levelMeta[r.level].color }">
                {{ levelMeta[r.level].label }}
              </span>
              <el-switch
                :model-value="r.enabled === 1"
                size="small"
                inline-prompt
                active-text="启" inactive-text="停"
                @click.stop
                @change="toggleEnabled(r)" />
            </div>
          </div>
          <div class="rule-title">{{ r.title }}</div>
          <div class="rule-cat">{{ r.topCategory }}<span v-if="r.subCategory"> · {{ r.subCategory }}</span></div>
          <div class="rule-preview">{{ (r.body || '').slice(0, 80) }}{{ (r.body || '').length > 80 ? '...' : '' }}</div>
        </div>
      </div>

      <div v-if="filteredRules.length === 0 && !loading" class="empty-state">
        <div class="empty-icon">🔍</div>
        <div>没有匹配的规约</div>
      </div>
    </main>

    <!-- 详情抽屉 -->
    <el-drawer :model-value="!!selectedRule" @close="selectedRule = null"
               :with-header="false" size="620px" direction="rtl">
      <div v-if="selectedRule" class="detail">
        <div class="d-header">
          <div class="d-badges">
            <span class="d-code">{{ selectedRule.code }}</span>
            <span class="d-level"
                  :style="{ background: levelMeta[selectedRule.level].bg, color: levelMeta[selectedRule.level].color }">
              【{{ levelMeta[selectedRule.level].label }}】
            </span>
            <span class="d-cat">{{ selectedRule.topCategory }}<span v-if="selectedRule.subCategory"> · {{ selectedRule.subCategory }}</span></span>
            <span v-if="selectedRule.isCustom === 1" class="badge-custom">自定义</span>
          </div>
          <div class="d-actions">
            <el-switch
              :model-value="selectedRule.enabled === 1"
              size="small"
              inline-prompt
              active-text="启用" inactive-text="禁用"
              @change="toggleEnabled(selectedRule)" />
            <el-button v-if="selectedRule.isCustom === 1" type="danger" size="small" link
                       @click="deleteCustom(selectedRule)">删除</el-button>
            <el-button circle size="small" @click="selectedRule = null">×</el-button>
          </div>
        </div>

        <h2 class="d-title">{{ selectedRule.title }}</h2>

        <div v-if="selectedRule.body" class="d-section">
          <div class="d-h">📝 规约说明</div>
          <div class="d-body">{{ selectedRule.body }}</div>
        </div>

        <div v-if="selectedRule.description" class="d-section">
          <div class="d-h">💬 说明</div>
          <div class="d-body">{{ selectedRule.description }}</div>
        </div>

        <div v-if="selectedRule.exampleBad" class="d-section">
          <div class="d-h d-h-bad">❌ 反例</div>
          <pre class="d-code d-code-bad">{{ selectedRule.exampleBad }}</pre>
        </div>

        <div v-if="selectedRule.exampleGood" class="d-section">
          <div class="d-h d-h-good">✅ 正例</div>
          <pre class="d-code d-code-good">{{ selectedRule.exampleGood }}</pre>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.rules-page { display: grid; grid-template-columns: 260px 1fr; gap: 16px; padding: 20px; height: 100%; }

/* 侧边栏 */
.side {
  background: #fff; border-radius: 12px; padding: 0;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  overflow-y: auto; display: flex; flex-direction: column;
}
.side-header {
  padding: 16px; border-bottom: 1px solid #e5e7eb;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff; border-radius: 12px 12px 0 0;
}
.side-title { font-size: 15px; font-weight: 600; }
.side-sub { font-size: 12px; opacity: 0.85; margin-top: 2px; }

.quick-filters { padding: 12px; display: flex; flex-direction: column; gap: 4px; border-bottom: 1px solid #e5e7eb; }
.qf-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 7px 10px; border-radius: 6px; cursor: pointer;
  font-size: 13px; color: #374151; transition: all 0.15s;
}
.qf-item:hover { background: #f3f4f6; }
.qf-item.on { background: #ede9fe; color: #6d28d9; font-weight: 500; }
.qf-item.qf-red.on    { background: #fef2f2; color: #dc2626; }
.qf-item.qf-yellow.on { background: #fffbeb; color: #d97706; }
.qf-item.qf-blue.on   { background: #eff6ff; color: #2563eb; }
.qf-item.qf-gray.on   { background: #f9fafb; color: #4b5563; }
.qf-item.qf-purple.on { background: #f5f3ff; color: #7c3aed; }
.qf-count { font-size: 11px; color: #9ca3af; background: #f3f4f6; padding: 1px 8px; border-radius: 10px; }
.qf-item.on .qf-count { background: #fff; color: inherit; }

.cat-divider { padding: 10px 16px 6px; font-size: 11px; color: #9ca3af; font-weight: 600; letter-spacing: 1px; }

.cat-tree { flex: 1; overflow-y: auto; padding: 0 12px 12px; }
.cat-group { margin-bottom: 2px; }
.cat-top {
  display: flex; align-items: center; gap: 6px;
  padding: 7px 10px; border-radius: 6px; cursor: pointer;
  font-size: 13px; color: #374151; transition: all 0.15s;
}
.cat-top:hover { background: #f3f4f6; }
.cat-top.on { background: #ede9fe; color: #6d28d9; font-weight: 500; }
.cat-arrow { font-size: 10px; color: #9ca3af; transition: transform 0.2s; display: inline-block; }
.cat-arrow.rotated { transform: rotate(90deg); }
.cat-icon { font-size: 14px; }
.cat-name { flex: 1; }
.cat-count { font-size: 11px; color: #9ca3af; background: #f3f4f6; padding: 1px 7px; border-radius: 10px; }
.cat-top.on .cat-count { background: #fff; color: #6d28d9; }

.cat-children { padding-left: 32px; }
.cat-child {
  display: flex; justify-content: space-between; align-items: center;
  padding: 5px 10px; border-radius: 5px; cursor: pointer;
  font-size: 12px; color: #6b7280;
}
.cat-child:hover { background: #f3f4f6; }
.cat-child.on { background: #faf5ff; color: #6d28d9; font-weight: 500; }

/* 主区域 */
.main {
  background: #fff; border-radius: 12px; padding: 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06); overflow-y: auto;
}
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 18px; gap: 20px; }
.page-title { font-size: 18px; font-weight: 600; color: #111827; margin-bottom: 4px; }
.page-sub { color: #6b7280; font-size: 13px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.stat-pill { padding: 2px 10px; border-radius: 10px; font-size: 11px; font-weight: 600; }
.stat-pill.red    { background: #fef2f2; color: #dc2626; }
.stat-pill.yellow { background: #fffbeb; color: #d97706; }
.stat-pill.blue   { background: #eff6ff; color: #2563eb; }
.search-input { width: 360px; flex-shrink: 0; }

.rules-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 12px; }
.rule-card {
  padding: 14px; border: 1px solid #e5e7eb; border-radius: 10px;
  cursor: pointer; transition: all 0.2s;
  background: #fff; position: relative;
}
.rule-card:hover {
  border-color: #6d28d9; transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(109, 40, 217, 0.1);
}
.rule-card[data-active="true"] { border-color: #6d28d9; background: #faf5ff; }
.rule-card.lv-mandatory   { border-left: 4px solid #ef4444; }
.rule-card.lv-recommended { border-left: 4px solid #f59e0b; }
.rule-card.lv-reference   { border-left: 4px solid #3b82f6; }
.rule-card.disabled { opacity: 0.55; background: #f9fafb; }

.rule-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; gap: 8px; }
.rule-top-right { display: flex; align-items: center; gap: 6px; }
.rule-code { font-family: 'SF Mono', Menlo, monospace; font-size: 11px; color: #6d28d9; font-weight: 600; }
.rule-level { padding: 2px 8px; border-radius: 10px; font-size: 11px; font-weight: 600; }
.badge-custom {
  background: #f5f3ff; color: #7c3aed; font-size: 10px; font-weight: 600;
  padding: 1px 7px; border-radius: 10px;
}
.rule-title { font-size: 14px; color: #111827; font-weight: 500; margin-bottom: 4px; line-height: 1.5; }
.rule-cat { font-size: 11px; color: #9ca3af; margin-bottom: 6px; }
.rule-preview {
  font-size: 12px; color: #6b7280; line-height: 1.55;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
  overflow: hidden;
}

.empty-state { text-align: center; padding: 60px 20px; color: #9ca3af; }
.empty-icon { font-size: 64px; opacity: 0.3; margin-bottom: 10px; }

/* 详情抽屉 */
.detail { padding: 4px 6px; }
.d-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px; }
.d-badges { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.d-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.d-code { font-family: monospace; font-size: 13px; color: #6d28d9; font-weight: 600; background: #faf5ff; padding: 3px 10px; border-radius: 4px; }
.d-level { padding: 3px 10px; border-radius: 10px; font-size: 12px; font-weight: 600; }
.d-cat { color: #6b7280; font-size: 12px; }
.d-title { font-size: 22px; font-weight: 600; margin: 0 0 22px; color: #111827; line-height: 1.4; }
.d-section { margin-bottom: 22px; }
.d-h { font-weight: 600; margin-bottom: 10px; color: #374151; font-size: 14px; }
.d-h-good { color: #059669; }
.d-h-bad { color: #dc2626; }
.d-body { color: #4b5563; line-height: 1.8; font-size: 14px; white-space: pre-wrap; }
.d-code {
  padding: 14px; border-radius: 8px; font-size: 13px;
  font-family: 'SF Mono', Menlo, monospace; overflow-x: auto; line-height: 1.7;
  white-space: pre-wrap;
}
.d-code-bad  { background: #fef2f2; color: #7f1d1d; border-left: 3px solid #ef4444; }
.d-code-good { background: #f0fdf4; color: #14532d; border-left: 3px solid #10b981; }
</style>
