/**
 * useImportSse —— 订阅 /api/v1/notes/import/tasks/:id/stream
 *
 * 用 fetch + ReadableStream 替代原生 EventSource，因为后者不支持自定义 Authorization 头。
 * 事件类型与后端 NoteImportPipeline 对齐：stage / item-progress / item-done / task-done / error
 */

import { ref, onUnmounted } from 'vue'
import { useUserStore } from '@/stores/user'

export interface ImportSseHandlers {
  onStage?:    (data: { taskId?: number; itemId?: number; stage: string; fileName?: string; title?: string }) => void
  onItemDone?: (data: { itemId: number; parsedTitle?: string; conflictWithNoteId?: number; enrichStatus?: string; reason?: string }) => void
  onTaskDone?: (data: { taskId: number; processed: number; failed: number }) => void
  onError?:    (data: { itemId?: number; msg: string }) => void
}

export function useImportSse() {
  const streaming = ref(false)
  let abort: AbortController | null = null

  async function start(taskId: number, handlers: ImportSseHandlers) {
    const userStore = useUserStore()
    streaming.value = true
    abort = new AbortController()
    try {
      const resp = await fetch(`/api/v1/notes/import/tasks/${taskId}/stream`, {
        method: 'GET',
        signal: abort.signal,
        headers: {
          Accept: 'text/event-stream',
          ...(userStore.token ? { Authorization: `Bearer ${userStore.token}` } : {}),
        },
      })
      if (!resp.ok || !resp.body) {
        handlers.onError?.({ msg: `SSE 连接失败 (${resp.status})` })
        return
      }
      const reader = resp.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const chunks = buffer.split('\n\n')
        buffer = chunks.pop() || ''
        for (const chunk of chunks) dispatch(chunk, handlers)
      }
      if (buffer.trim()) dispatch(buffer, handlers)
    } catch (e: unknown) {
      if ((e as Error).name !== 'AbortError') {
        handlers.onError?.({ msg: (e as Error).message })
      }
    } finally {
      streaming.value = false
      abort = null
    }
  }

  function dispatch(chunk: string, h: ImportSseHandlers) {
    let event = 'message'
    let dataRaw = ''
    for (const line of chunk.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) dataRaw += line.slice(5).trim()
    }
    if (!dataRaw) return
    let payload: unknown = dataRaw
    try { payload = JSON.parse(dataRaw) } catch {/* keep raw */}
    switch (event) {
      case 'stage':     h.onStage?.(payload as never); break
      case 'item-done': h.onItemDone?.(payload as never); break
      case 'task-done': h.onTaskDone?.(payload as never); break
      case 'error':     h.onError?.(payload as never); break
    }
  }

  function stop() { abort?.abort(); streaming.value = false }
  onUnmounted(stop)
  return { streaming, start, stop }
}
