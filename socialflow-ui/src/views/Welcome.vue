<!--
  Welcome.vue —— 欢迎首页
  登录后 / 浏览器刷新后的默认落地页，引导用户进入两大核心模块。
-->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

/** 按当前时段返回问候语 */
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6)  return '凌晨好'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  if (h < 22) return '晚上好'
  return '夜深了'
})

/** 当前日期 yyyy 年 MM 月 dd 日 星期 */
const todayStr = computed(() => {
  const d = new Date()
  const week = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'][d.getDay()]
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日 · ${week}`
})

/** 小时计时，用于 Hero 右侧动态时钟 */
const now = ref(new Date())
const timeStr = computed(() =>
  `${String(now.value.getHours()).padStart(2, '0')}:${String(now.value.getMinutes()).padStart(2, '0')}`)

onMounted(() => {
  const timer = window.setInterval(() => { now.value = new Date() }, 1000 * 30)
  // 组件卸载时停掉计时器（Welcome 一般只进入一次，这里仅示范）
  return () => window.clearInterval(timer)
})

/** 两大核心模块的入口 */
const pillars = [
  {
    key: 'content',
    title: '文案创作',
    subtitle: 'AI 文案 · 多智能体协作 · 知识库 RAG',
    gradient: 'aurora',
    icon: 'MagicStick',
    to: '/workspace',
    tags: ['内容库', '素材库', '模板', '评估', '分发'],
  },
  {
    key: 'code',
    title: '代码分析',
    subtitle: 'AI 审查 · 321 条阿里规约 · 误判闭环',
    gradient: 'ocean',
    icon: 'Cpu',
    to: '/code-analysis/dashboard',
    tags: ['项目概览', '提交审查', '对比分析', '规约库', 'LLM 链路'],
  },
]

/** 细分功能快捷入口 */
const features = [
  { icon: 'MagicStick',  title: 'AI 创作工作台', desc: '多智能体协作生成社媒文案', to: '/workspace',          bg: 'is-brand' },
  { icon: 'Files',       title: '内容库',       desc: '管理已生成的所有文案',    to: '/content',           bg: 'is-info' },
  { icon: 'Picture',     title: '素材库',       desc: '图片/视频/音频素材管理',   to: '/media',             bg: 'is-success' },
  { icon: 'Reading',     title: '知识库',       desc: 'RAG 向量化检索增强',      to: '/knowledge',         bg: 'is-sunset' },
  { icon: 'Collection',  title: 'Prompt 模板',  desc: '预设提示词 · 一键套用',    to: '/template',          bg: 'is-rose' },
  { icon: 'DataAnalysis',title: '评估中心',     desc: '内容质量打分与 A/B 对比', to: '/eval',              bg: 'is-forest' },
  { icon: 'Promotion',   title: '分发中心',     desc: '多平台一键发布',          to: '/publish',           bg: 'is-warning' },
  { icon: 'TrendCharts', title: '数据看板',     desc: '发布表现与内容分析',      to: '/dashboard',         bg: 'is-danger' },
]
</script>

<template>
  <div class="welcome-page">
    <!-- Hero 区 -->
    <div class="welcome-hero">
      <!-- 背景装饰：星点 + 光斑 + 网格 -->
      <div class="wh-stars">
        <span v-for="i in 40" :key="i" class="wh-star"
              :style="{
                top: (i * 37 % 100) + '%',
                left: (i * 71 % 100) + '%',
                animationDelay: (i * 0.13) + 's',
                width: (1 + (i % 3)) + 'px',
                height: (1 + (i % 3)) + 'px',
              }" />
      </div>
      <div class="wh-glow wh-glow-1"></div>
      <div class="wh-glow wh-glow-2"></div>
      <div class="wh-grid"></div>

      <!-- 左：问候 + 产品介绍 -->
      <div class="wh-main">
        <div class="wh-eyebrow">
          <span class="wh-live-dot"></span>
          {{ todayStr }}
        </div>
        <h1 class="wh-title">
          {{ greeting }}，<span class="wh-name">{{ userStore.user?.nickname || '创作者' }}</span>
          <span class="wh-wave">👋</span>
        </h1>
        <p class="wh-subtitle">
          欢迎回到 <strong>SocialFlow</strong> —— 一体化 AI 社媒内容运营平台。
          <br />今天想做点什么？
        </p>

        <!-- 顶层快捷按钮 -->
        <div class="wh-actions">
          <button class="wh-action wh-action-primary" @click="router.push('/workspace')">
            <el-icon><MagicStick /></el-icon>
            开始创作
          </button>
          <button class="wh-action wh-action-ghost" @click="router.push('/code-analysis/project')">
            <el-icon><Cpu /></el-icon>
            分析代码
          </button>
        </div>
      </div>

      <!-- 右：数字时钟 + 品牌字母 -->
      <div class="wh-side">
        <div class="wh-clock">
          <div class="wh-clock-time">{{ timeStr }}</div>
          <div class="wh-clock-label">Local Time</div>
        </div>
        <div class="wh-brand">SF</div>
      </div>
    </div>

    <!-- 两大核心模块入口 -->
    <div class="welcome-pillars">
      <div v-for="p in pillars" :key="p.key" class="pillar-card" :class="'is-' + p.gradient"
           @click="router.push(p.to)">
        <div class="pillar-icon"><el-icon><component :is="p.icon" /></el-icon></div>
        <div class="pillar-body">
          <div class="pillar-title">{{ p.title }}</div>
          <div class="pillar-sub">{{ p.subtitle }}</div>
          <div class="pillar-tags">
            <span v-for="t in p.tags" :key="t" class="pillar-tag">{{ t }}</span>
          </div>
        </div>
        <div class="pillar-arrow"><el-icon><Right /></el-icon></div>
      </div>
    </div>

    <!-- 全部功能快捷入口 -->
    <div class="welcome-section">
      <div class="section-head">
        <div class="section-title">全部功能</div>
        <div class="section-sub">快速跳转到任一模块</div>
      </div>
      <div class="feature-grid">
        <div v-for="(f, i) in features" :key="f.to" class="feature-card sf-stagger"
             :style="{ animationDelay: (i * 0.04) + 's' }" @click="router.push(f.to)">
          <div class="feature-icon" :class="f.bg"><el-icon><component :is="f.icon" /></el-icon></div>
          <div class="feature-title">{{ f.title }}</div>
          <div class="feature-desc">{{ f.desc }}</div>
          <div class="feature-arrow"><el-icon><Right /></el-icon></div>
        </div>
      </div>
    </div>

    <!-- 底部 slogan -->
    <div class="welcome-footer">
      <div class="footer-stars">
        <el-icon><MagicStick /></el-icon>
      </div>
      <div class="footer-text">
        由 AI 驱动的创作与代码分析平台
      </div>
    </div>
  </div>
</template>

<style scoped>
.welcome-page {
  padding: var(--sf-space-5);
  max-width: 1280px;
  margin: 0 auto;
  animation: sf-fade-in var(--sf-transition-base);
}

/* ================= Hero ================= */
.welcome-hero {
  position: relative;
  background: linear-gradient(125deg, #1e1b4b 0%, #4c1d95 40%, #7e22ce 75%, #be185d 100%);
  border-radius: var(--sf-radius-xl);
  padding: var(--sf-space-7) var(--sf-space-7);
  color: #fff;
  overflow: hidden;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--sf-space-6);
  min-height: 280px;
  box-shadow: 0 24px 48px rgba(75, 30, 133, 0.25);
  margin-bottom: var(--sf-space-5);
}

/* 星空效果 */
.wh-stars {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.wh-star {
  position: absolute;
  background: #fff;
  border-radius: 50%;
  opacity: 0.4;
  animation: wh-twinkle 4s ease-in-out infinite;
}
@keyframes wh-twinkle {
  0%, 100% { opacity: 0.15; transform: scale(0.8); }
  50%      { opacity: 0.9;  transform: scale(1.1); }
}

/* 光斑装饰 */
.wh-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  pointer-events: none;
}
.wh-glow-1 {
  width: 480px; height: 480px;
  top: -120px; right: -100px;
  background: radial-gradient(circle, rgba(236, 72, 153, 0.35) 0%, transparent 70%);
  animation: sf-float 10s ease-in-out infinite;
}
.wh-glow-2 {
  width: 420px; height: 420px;
  bottom: -180px; left: 15%;
  background: radial-gradient(circle, rgba(102, 126, 234, 0.4) 0%, transparent 70%);
  animation: sf-float 12s ease-in-out infinite reverse;
}
/* 网格 */
.wh-grid {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(circle, rgba(255, 255, 255, 0.1) 1px, transparent 1px);
  background-size: 28px 28px;
  mask-image: radial-gradient(ellipse at center, #000 30%, transparent 80%);
  -webkit-mask-image: radial-gradient(ellipse at center, #000 30%, transparent 80%);
  opacity: 0.3;
  pointer-events: none;
}

.wh-main {
  position: relative;
  z-index: 1;
  flex: 1;
  min-width: 0;
}
.wh-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  letter-spacing: 0.15em;
  opacity: 0.85;
  text-transform: uppercase;
  margin-bottom: var(--sf-space-3);
  font-weight: 500;
}
.wh-live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #4ade80;
  box-shadow: 0 0 0 4px rgba(74, 222, 128, 0.3);
  animation: sf-glow-pulse 1.8s ease-in-out infinite;
}
.wh-title {
  font-size: 40px;
  font-weight: 800;
  margin: 0 0 var(--sf-space-3);
  letter-spacing: -0.03em;
  line-height: 1.1;
}
.wh-name {
  background: linear-gradient(90deg, #fbbf24, #f472b6, #a78bfa);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.wh-wave {
  display: inline-block;
  animation: wh-wave 2.2s ease-in-out infinite;
  transform-origin: 70% 70%;
  margin-left: 6px;
}
@keyframes wh-wave {
  0%, 100% { transform: rotate(0deg); }
  15%      { transform: rotate(14deg); }
  30%      { transform: rotate(-8deg); }
  45%      { transform: rotate(14deg); }
  60%      { transform: rotate(-4deg); }
  75%      { transform: rotate(10deg); }
}
.wh-subtitle {
  margin: 0 0 var(--sf-space-5);
  font-size: 15px;
  line-height: 1.8;
  opacity: 0.92;
}
.wh-subtitle strong { color: #fff; font-weight: 700; }

/* 顶部快捷按钮 */
.wh-actions {
  display: flex;
  gap: var(--sf-space-3);
}
.wh-action {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  border-radius: var(--sf-radius-full);
  font-size: 14.5px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--sf-transition-base);
  border: none;
}
.wh-action-primary {
  background: #fff;
  color: #4c1d95;
  box-shadow: 0 8px 24px rgba(255, 255, 255, 0.25);
}
.wh-action-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 32px rgba(255, 255, 255, 0.35);
}
.wh-action-ghost {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(8px);
}
.wh-action-ghost:hover {
  background: rgba(255, 255, 255, 0.22);
  transform: translateY(-2px);
}

/* 右侧：时钟 + 品牌字母 */
.wh-side {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--sf-space-4);
}
.wh-clock {
  text-align: right;
}
.wh-clock-time {
  font-size: 48px;
  font-weight: 300;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
  line-height: 1;
  background: linear-gradient(135deg, #fff, rgba(255,255,255,0.6));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.wh-clock-label {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.2em;
  opacity: 0.6;
  margin-top: 4px;
}
.wh-brand {
  font-size: 120px;
  font-weight: 900;
  line-height: 1;
  letter-spacing: -0.05em;
  background: linear-gradient(135deg, rgba(255,255,255,0.3), rgba(255,255,255,0.05));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  pointer-events: none;
  user-select: none;
}

/* ================= 两大核心入口 ================= */
.welcome-pillars {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sf-space-4);
  margin-bottom: var(--sf-space-6);
}
.pillar-card {
  position: relative;
  background: var(--sf-surface);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-lg);
  padding: var(--sf-space-5) var(--sf-space-6);
  display: flex;
  align-items: center;
  gap: var(--sf-space-5);
  cursor: pointer;
  overflow: hidden;
  transition: all var(--sf-transition-base);
  box-shadow: var(--sf-shadow-sm);
}
.pillar-card::before {
  content: '';
  position: absolute;
  inset: 0;
  opacity: 0;
  transition: opacity var(--sf-transition-base);
  pointer-events: none;
}
.pillar-card.is-aurora::before { background: linear-gradient(135deg, rgba(102,126,234,0.08), rgba(236,72,153,0.08)); }
.pillar-card.is-ocean::before  { background: linear-gradient(135deg, rgba(79,172,254,0.08), rgba(0,242,254,0.08)); }
.pillar-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--sf-shadow-lg);
  border-color: transparent;
}
.pillar-card:hover::before { opacity: 1; }
.pillar-card > * { position: relative; z-index: 1; }

.pillar-icon {
  width: 72px;
  height: 72px;
  border-radius: var(--sf-radius-lg);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 36px;
  box-shadow: var(--sf-shadow-md);
}
.pillar-card.is-aurora .pillar-icon { background: var(--sf-gradient-aurora); }
.pillar-card.is-ocean .pillar-icon  { background: var(--sf-gradient-ocean); }

.pillar-body { flex: 1; min-width: 0; }
.pillar-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--sf-text-primary);
  margin-bottom: 4px;
  letter-spacing: -0.01em;
}
.pillar-sub {
  font-size: 13px;
  color: var(--sf-text-tertiary);
  margin-bottom: var(--sf-space-3);
  line-height: 1.5;
}
.pillar-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.pillar-tag {
  padding: 3px 10px;
  background: var(--sf-bg-subtle);
  border: 1px solid var(--sf-border-light);
  color: var(--sf-text-tertiary);
  border-radius: var(--sf-radius-full);
  font-size: 11.5px;
  font-weight: 500;
}
.pillar-card.is-aurora .pillar-tag { background: rgba(102,126,234,0.08); color: var(--sf-primary-dark); border-color: rgba(102,126,234,0.15); }
.pillar-card.is-ocean .pillar-tag  { background: rgba(6,182,212,0.08); color: #0891b2; border-color: rgba(6,182,212,0.18); }

.pillar-arrow {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--sf-bg-subtle);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--sf-text-tertiary);
  font-size: 20px;
  transition: all var(--sf-transition-base);
  flex-shrink: 0;
}
.pillar-card:hover .pillar-arrow {
  background: var(--sf-gradient);
  color: #fff;
  transform: translateX(4px);
}
.pillar-card.is-ocean:hover .pillar-arrow { background: var(--sf-gradient-ocean); }

/* ================= 功能网格 ================= */
.welcome-section { margin-bottom: var(--sf-space-6); }
.section-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: var(--sf-space-4);
}
.section-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--sf-text-primary);
  letter-spacing: -0.01em;
}
.section-sub {
  font-size: 12.5px;
  color: var(--sf-text-muted);
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--sf-space-3);
}
@media (max-width: 1100px) { .feature-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 600px)  { .feature-grid { grid-template-columns: 1fr; } }

.feature-card {
  position: relative;
  background: var(--sf-surface);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-md);
  padding: var(--sf-space-4);
  cursor: pointer;
  transition: all var(--sf-transition-base);
}
.feature-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--sf-shadow-md);
  border-color: var(--sf-primary-light);
}
.feature-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--sf-radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 22px;
  margin-bottom: var(--sf-space-3);
  box-shadow: var(--sf-shadow-sm);
}
.feature-icon.is-brand   { background: var(--sf-gradient-aurora); }
.feature-icon.is-info    { background: var(--sf-info-gradient); }
.feature-icon.is-success { background: var(--sf-success-gradient); }
.feature-icon.is-warning { background: var(--sf-warning-gradient); }
.feature-icon.is-danger  { background: var(--sf-danger-gradient); }
.feature-icon.is-sunset  { background: var(--sf-gradient-sunset); }
.feature-icon.is-forest  { background: var(--sf-gradient-forest); }
.feature-icon.is-rose    { background: var(--sf-gradient-rose); }

.feature-title {
  font-size: 14.5px;
  font-weight: 600;
  color: var(--sf-text-primary);
  margin-bottom: 4px;
}
.feature-desc {
  font-size: 12.5px;
  color: var(--sf-text-tertiary);
  line-height: 1.5;
}
.feature-arrow {
  position: absolute;
  top: var(--sf-space-4);
  right: var(--sf-space-4);
  color: var(--sf-text-muted);
  font-size: 14px;
  opacity: 0;
  transform: translateX(-4px);
  transition: all var(--sf-transition-base);
}
.feature-card:hover .feature-arrow {
  opacity: 1;
  transform: translateX(0);
  color: var(--sf-primary);
}

/* ================= 底部 slogan ================= */
.welcome-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sf-space-2);
  padding: var(--sf-space-5) 0;
  color: var(--sf-text-muted);
  font-size: 13px;
}
.footer-stars {
  color: var(--sf-primary);
  animation: sf-float 3s ease-in-out infinite;
}
</style>
