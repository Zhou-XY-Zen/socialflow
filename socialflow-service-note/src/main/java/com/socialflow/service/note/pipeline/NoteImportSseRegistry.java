package com.socialflow.service.note.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 导入任务的 SseEmitter 注册中心
 *
 * Pipeline 在后台线程产出事件 → 通过 push() 广播到所有订阅了该 taskId 的 emitter。
 * Controller 创建 emitter → register 到这里，emitter 自动在 timeout/error/complete
 * 时调用 unregister。
 *
 * 关键：注入 Spring 管理的 ObjectMapper（带 JacksonConfig.longToStringCustomizer），
 * 否则 SSE 推送的雪花 ID（Long）会变成裸 JSON Number，浏览器解析时丢失精度
 * （Long > 2^53 时 JS Number 会四舍五入）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoteImportSseRegistry {

    private final ObjectMapper objectMapper;

    private final Map<Long, List<SseEmitter>> store = new ConcurrentHashMap<>();

    public SseEmitter create(Long taskId) {
        SseEmitter em = new SseEmitter(0L); // 不主动超时
        store.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(em);
        em.onCompletion(() -> remove(taskId, em));
        em.onTimeout(()    -> remove(taskId, em));
        em.onError(t       -> remove(taskId, em));
        return em;
    }

    public void push(Long taskId, String event, Object data) {
        List<SseEmitter> list = store.get(taskId);
        if (list == null || list.isEmpty()) return;
        String json;
        try { json = objectMapper.writeValueAsString(data); }
        catch (Exception e) { json = "{}"; }
        for (SseEmitter em : list) {
            try {
                em.send(SseEmitter.event().name(event).data(json));
            } catch (IOException e) {
                log.debug("sse send failed, removing emitter: {}", e.getMessage());
                remove(taskId, em);
            }
        }
    }

    public void complete(Long taskId) {
        List<SseEmitter> list = store.remove(taskId);
        if (list == null) return;
        for (SseEmitter em : list) {
            try { em.complete(); } catch (Exception ignored) {}
        }
    }

    private void remove(Long taskId, SseEmitter em) {
        List<SseEmitter> list = store.get(taskId);
        if (list != null) {
            list.remove(em);
            if (list.isEmpty()) store.remove(taskId);
        }
    }
}
