package com.lenovo.ai.infra.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class AiChatSseEmitter extends SseEmitter {

    private static final long DEFAULT_TIMEOUT_MS = 300_000L;

    /** 根 emitter；子视图非 null，根为 null */
    private volatile AiChatSseEmitter parent;
    /** 根引用缓存，避免每次沿链向上遍历 */
    private volatile AiChatSseEmitter rootRef;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<Runnable> closeCallbacks = new CopyOnWriteArrayList<>();
    private final Object closeLock = new Object();

    private volatile boolean muted = false;
    private volatile boolean suppressComplete = false;

    public AiChatSseEmitter() {
        super(DEFAULT_TIMEOUT_MS);
        this.rootRef = this;
    }

    // ===================== 标志 =====================

    public void mute() { this.muted = true; }
    public void unmute() { this.muted = false; }
    public boolean isMuted() { return muted; }
    public void setSuppressComplete(boolean b) { this.suppressComplete = b; }
    public boolean isSuppressComplete() { return suppressComplete; }

    // ===================== 子视图 =====================

    public AiChatSseEmitter childView() {
        AiChatSseEmitter view = new AiChatSseEmitter();
        view.parent = this;
        view.rootRef = this.root();          // 直接指向根，O(1)
        view.muted = this.muted;             // 继承静默状态
        view.suppressComplete = this.suppressComplete;
        return view;
    }

    public boolean isChildView() { return parent != null; }

    private AiChatSseEmitter root() {
        return rootRef != null ? rootRef : this;
    }

    // ===================== 关闭回调 =====================

    public void addCloseCallback(Runnable callback) {
        if (callback == null) return;
        boolean runNow = false;
        synchronized (closeLock) {
            if (closed.get()) {
                runNow = true;
            } else {
                closeCallbacks.add(callback);
            }
        }
        if (runNow) {
            safeRun(callback);
        }
    }

    @Deprecated
    public void setOnCloseCallback(Runnable callback) {
        addCloseCallback(callback);
    }

    public boolean isClosed() {
        return parent != null ? root().isClosed() : closed.get();
    }

    private void safeRun(Runnable cb) {
        try { cb.run(); } catch (Exception e) {
            log.warn("SSE close callback 异常", e);
        }
    }

    /**
     * 锁内只做状态置位 + 拷贝回调，锁外执行，避免持锁跑用户逻辑。
     */
    private void doClose(Throwable err) {
        if (!closed.compareAndSet(false, true)) return;

        // 子视图：不关底层连接，只跑自己的回调
        if (parent != null) {
            log.debug(">>> SSE 子视图结束（不关闭根连接）");
            fireCallbacks();
            return;
        }

        try {
            if (err != null) {
                super.completeWithError(err);
            } else {
                super.complete();
            }
        } catch (Exception e) {
            log.warn("SSE 关闭异常", e);
        } finally {
            fireCallbacks();
        }
    }

    private void fireCallbacks() {
        List<Runnable> toRun;
        synchronized (closeLock) {
            if (closeCallbacks.isEmpty()) return;
            toRun = new ArrayList<>(closeCallbacks);
            closeCallbacks.clear();
        }
        for (Runnable cb : toRun) {
            safeRun(cb);
        }
    }

    // ===================== send =====================

    private boolean shouldDrop() {
        return muted || isClosed();
    }

    public void send(String chunk) {
        if (shouldDrop()) return;
        if (chunk == null || chunk.isEmpty()) return;

        if (parent != null) {
            root().send(chunk);
            return;
        }
        try {
            super.send(SseEmitter.event()
                    .name("delta")
                    .data(chunk, MediaType.TEXT_PLAIN));
        } catch (IOException | IllegalStateException e) {
            log.warn("SSE send 失败: {}", e.getMessage());
            doClose(null);
        }
    }

    public void send(String eventName, Object data) {
        if (shouldDrop()) return;

        String name = (eventName == null || eventName.isEmpty()) ? "message" : eventName;

        if (parent != null) {
            if ("done".equals(name) || "complete".equals(name)) {
                log.debug(">>> SSE 子视图吞掉终结帧: {}", name);
                return;
            }
            root().send(name, data);
            return;
        }

        try {
            super.send(SseEmitter.event()
                    .name(name)
                    .data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            log.warn("SSE send 失败: {}", e.getMessage());
            doClose(null);
        }
    }

    // ===================== 终结 =====================

    @Override
    public void complete() {
        if (suppressComplete) {
            log.debug(">>> SSE complete() 被抑制");
            return;
        }
        doClose(null);
    }

    @Override
    public void completeWithError(Throwable ex) {
        // 子视图异常：标记自身 + 通知根，避免 Orchestrator 无感知
        if (parent != null) {
            log.warn(">>> SSE 子视图异常，通知根: {}", ex == null ? "null" : ex.getMessage());
            doClose(ex);
            root().doClose(ex);
            return;
        }
        doClose(ex);
    }
}