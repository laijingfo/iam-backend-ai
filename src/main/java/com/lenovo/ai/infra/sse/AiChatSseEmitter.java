package com.lenovo.ai.infra.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class AiChatSseEmitter extends SseEmitter {

    private static final long DEFAULT_TIMEOUT_MS = 300_000L;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<Runnable> closeCallbacks = new CopyOnWriteArrayList<>();

    private volatile boolean muted = false;
    private volatile boolean suppressComplete = false;

    /** 静默 / 抑制标志 */
    public void mute() { this.muted = true; }
    public void unmute() { this.muted = false; }
    public boolean isMuted() { return muted; }
    public void setSuppressComplete(boolean b) { this.suppressComplete = b; }
    public boolean isSuppressComplete() { return suppressComplete; }

    public AiChatSseEmitter() {
        super(DEFAULT_TIMEOUT_MS);
    }

    /** 注册关闭回调（可多次注册，不覆盖） */
    public void addCloseCallback(Runnable callback) {
        if (callback == null) return;
        if (closed.get()) {
            try { callback.run(); } catch (Exception ignored) {}
        } else {
            closeCallbacks.add(callback);
        }
    }

    /** 兼容旧 API */
    @Deprecated
    public void setOnCloseCallback(Runnable callback) {
        addCloseCallback(callback);
    }

    public boolean isClosed() { return closed.get(); }

    /** 触发所有回调（幂等） */
    private void fireCallbacks() {
        for (Runnable cb : closeCallbacks) {
            try { cb.run(); } catch (Exception e) {
                log.warn("SSE close callback 异常", e);
            }
        }
        closeCallbacks.clear();
    }

    /** 标记关闭（幂等） */
    private void markClosed() {
        if (closed.compareAndSet(false, true)) {
            fireCallbacks();
        }
    }

    // ===================== send =====================

    public void send(String chunk) {
        if (muted) return;
        if (chunk == null || chunk.isEmpty()) return;
        if (closed.get()) return;
        try {
            super.send(SseEmitter.event()
                    .name("delta")
                    .data(chunk, MediaType.TEXT_PLAIN));
        } catch (IOException | IllegalStateException e) {
            log.warn("SSE send 失败: {}", e.getMessage());
            markClosed();
        }
    }

    public void send(String eventName, Object data) {
        if (muted) return;
        if (closed.get()) return;
        try {
            super.send(SseEmitter.event()
                    .name(eventName == null || eventName.isEmpty() ? "message" : eventName)
                    .data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            log.warn("SSE send 失败: {}", e.getMessage());
            markClosed();
        }
    }

    @Override
    public void complete() {
        if (suppressComplete) {
            log.debug(">>> SSE complete() 被抑制");
            return;
        }
        if (!closed.compareAndSet(false, true)) return;

        try {
            super.complete();
        } catch (Exception e) {
            log.warn("SSE complete 异常", e);
        } finally {
            fireCallbacks();
        }
    }

    @Override
    public void completeWithError(Throwable ex) {
        if (!closed.compareAndSet(false, true)) return;
        try {
            super.completeWithError(ex);
        } finally {
            fireCallbacks();
        }
    }
}