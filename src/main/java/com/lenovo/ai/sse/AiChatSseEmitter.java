package com.lenovo.ai.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class AiChatSseEmitter extends SseEmitter {

    /** 连接是否已关闭 */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** 关闭时的回调，由 runner 注册，用于立即停止 LLM 流 */
    private volatile Runnable onCloseCallback;

    public AiChatSseEmitter() {
        super(300_000L);   // 5 分钟

        // ✅ SseEmitter 生命周期结束（客户端断开、超时、服务端 complete）都会触发
        super.onCompletion(() -> {
            closed.set(true);
            Runnable cb = onCloseCallback;
            if (cb != null) {
                try { cb.run(); } catch (Exception ignored) {}
            }
        });
        super.onTimeout(() -> {
            closed.set(true);
            Runnable cb = onCloseCallback;
            if (cb != null) {
                try { cb.run(); } catch (Exception ignored) {}
            }
        });
    }

    public boolean isClosed() {
        return closed.get();
    }

    public void setOnCloseCallback(Runnable callback) {
        this.onCloseCallback = callback;
        // 如果注册之前就已经关闭了，立即回调
        if (closed.get() && callback != null) {
            try { callback.run(); } catch (Exception ignored) {}
        }
    }

    public void send(String chunk) {
        if (chunk == null || chunk.isEmpty()) return;
        if (closed.get()) return;
        try {
            super.send(SseEmitter.event()
                    .name("message")
                    .data(chunk, MediaType.TEXT_PLAIN));
        } catch (IOException e) {
            log.warn("SSE send failed (IOException): {}", e.getMessage());
            closed.set(true);
        } catch (IllegalStateException e) {
            log.warn("SSE already closed, ignore send.");
            closed.set(true);
        }
    }

    public void send(SseEmitter.SseEventBuilder event) {
        if (closed.get()) return;
        try {
            super.send(event);
        } catch (IOException e) {
            log.warn("SSE send failed (IOException): {}", e.getMessage());
            closed.set(true);
        } catch (IllegalStateException e) {
            log.warn("SSE already closed, ignore send.");
            closed.set(true);
        }
    }
}