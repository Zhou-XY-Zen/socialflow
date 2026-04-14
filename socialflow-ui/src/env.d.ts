/**
 * ============================================================
 * env.d.ts —— TypeScript 类型声明文件
 * ============================================================
 * 这个文件不会被编译成 JavaScript，也不会出现在最终打包产物中。
 * 它的唯一作用是"告诉 TypeScript 编译器一些额外的类型信息"，
 * 让 IDE（如 VS Code）能正确进行代码提示和类型检查。
 *
 * 文件名以 .d.ts 结尾的都是"声明文件"（Declaration File）。
 * ============================================================
 */

/**
 * 三斜线指令（Triple-Slash Directive）——
 * 引入 Vite 框架内置的类型定义，让 TypeScript 认识 import.meta.env 等 Vite 专有 API。
 */
/// <reference types="vite/client" />

/**
 * declare module '*.vue' —— 模块声明（Ambient Module Declaration）。
 *
 * TypeScript 默认不认识 .vue 后缀的文件。如果你在 .ts 文件中写
 *   import App from './App.vue'
 * TypeScript 会报错："找不到模块 './App.vue'"。
 *
 * 这段声明告诉 TypeScript：
 *   "所有 .vue 文件导出的内容都是一个 Vue 组件（DefineComponent 类型）"。
 * 这样 TypeScript 就不会报错了。
 *
 * DefineComponent<{}, {}, any> 是 Vue 3 提供的组件类型，
 * 三个泛型参数分别代表 Props、Emits 和其他选项，这里都用宽泛类型来兼容所有 .vue 文件。
 */
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}
