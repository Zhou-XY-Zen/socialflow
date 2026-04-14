<!--
  ============================================================
  App.vue —— 根组件（Root Component）
  ============================================================
  这是整个 Vue 应用最顶层的组件。所有页面内容都会被渲染在这个组件内部。
  它的结构非常简单：只放了一个 <router-view />，
  意思是"当前 URL 匹配到哪个路由，就在这里渲染对应的页面组件"。

  例如：
    - 访问 /login  → 渲染 Login.vue
    - 访问 /workspace → 渲染 Layout.vue（其中又嵌套了 Workspace.vue）
  ============================================================
-->

<script setup lang="ts">
/**
 * <script setup> 是 Vue 3 的语法糖（Composition API 的简写形式）。
 * 在 <script setup> 中声明的变量、函数、导入的组件都会自动暴露给模板使用，
 * 无需手动 return。
 *
 * lang="ts" 表示该脚本块使用 TypeScript 语法。
 */

/**
 * RouterView —— 来自 vue-router 的组件。
 * 它是一个"占位符"，Vue Router 会根据当前 URL 把匹配到的页面组件渲染到这里。
 */
import { RouterView } from 'vue-router'
</script>

<template>
  <!--
    <router-view /> 是路由出口。
    当 URL 变化时，Vue Router 会自动切换这里渲染的组件。
  -->
  <router-view />
</template>

<style>
/*
 * 全局样式：让 html、body、#app 这三层容器都占满整个视口高度，
 * 去除默认外边距，设置系统字体栈。
 */
html, body, #app {
  height: 100%;
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif;
}
</style>
