import { ref, onUnmounted } from 'vue'
import { useUserStore } from '@/stores/user'

export interface SseHandlers {
  onMessage?: (data: unknown) => void
  onStage?: (data: { stage: string; message?: string }) => void
  onGuardrail?: (data: { type: string; message: string }) => void
  onDone?: (data: { id: number; tokenUsage?: number }) => void
  onError?: (data: { code: number; message: string }) => void
}

/**
 * Minimal SSE client that works with Spring WebFlux Flux<String> SSE endpoints.
 * Uses fetch instead of EventSource so we can pass the bearer token via header.
 */
export function useSse() {
  const streaming = ref(false)
  let abort: AbortController | null = null

  async function start(url: string, body: unknown, handlers: SseHandlers) {
    const userStore = useUserStore()
    streaming.value = true
    abort = new AbortController()

    try {
      const response = await fetch(url, {
        method: 'POST',
        signal: abort.signal,
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          ...(userStore.token ? { Authorization: `Bearer ${userStore.token}` } : {}),
        },
        body: JSON.stringify(body),
      })

      if (!response.ok) {
        handlers.onError?.({ code: response.status, message: response.statusText })
        return
      }

      const reader = response.body!.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          // 流结束，处理缓冲区残余数据
          if (buffer.trim()) parseEvent(buffer, handlers)
          // 触发 onDone 回调（后端流式接口不一定会发 done 事件）
          handlers.onDone?.({ id: 0, tokenUsage: 0 })
          break
        }
        buffer += decoder.decode(value, { stream: true })
        const chunks = buffer.split('\n\n')
        buffer = chunks.pop() || ''
        for (const chunk of chunks) parseEvent(chunk, handlers)
      }
    } finally {
      streaming.value = false
      abort = null
    }
  }

  function parseEvent(chunk: string, handlers: SseHandlers) {
    let event = 'message'
    let data = ''
    for (const line of chunk.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) data += line.slice(5).trim()
    }
    if (!data) return
    let parsed: unknown = data
    try { parsed = JSON.parse(data) } catch { /* keep raw */ }
    switch (event) {
      case 'message':   handlers.onMessage?.(parsed); break
      case 'stage':     handlers.onStage?.(parsed as never); break
      case 'guardrail': handlers.onGuardrail?.(parsed as never); break
      case 'done':      handlers.onDone?.(parsed as never); break
      case 'error':     handlers.onError?.(parsed as never); break
    }
  }

  function stop() {
    abort?.abort()


    abort = null
    streaming.value = false
  }

  onUnmounted(stop)

  return { streaming, start, stop }
}
