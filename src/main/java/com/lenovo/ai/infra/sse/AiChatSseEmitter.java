package com.lenovo.ai.infra.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 对话专用 SSE 发射器。
 *
 * <p>在 SseEmitter 之上补三件事：</p>
 * <ol>
 *   <li><b>静默</b>：{@code mute()} 后所有 send 直接丢弃，用于内部 Agent
 *       （Planner / Synthesizer）不污染前端。</li>
 *   <li><b>抑制完成</b>：{@code suppressComplete=true} 时 {@link #complete()}
 *       变 no-op，避免第一个子任务跑完就把整条连接关掉。</li>
 *   <li><b>子视图</b>：{@link #childView()} 返回一个"只转发不终结"的视图，
 *       用于多 Agent 编排中的子任务——delta / tool 透传给根连接，
 *       但 done / complete 被吞掉，终结动作统一由 Orchestrator 发出。</li>
 * </ol>
 */
@Slf4j
public class AiChatSseEmitter extends SseEmitter {

    private static final long DEFAULT_TIMEOUT_MS = 300_000L;

    /** 子视图专用：指向父（根）emitter；根 emitter 为 null */
    private volatile AiChatSseEmitter parent;

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

    // ===================== 子视图 =====================

    /**
     * 创建子视图：delta / tool 等过程帧转发到根连接，
     * done / complete 等终结帧被丢弃。
     *
     * <p>用于 Orchestrator 的子任务，避免"子任务 1 跑完就关闭整条 SSE"。</p>
     */
    public AiChatSseEmitter childView() {
        AiChatSseEmitter view = new AiChatSseEmitter();
        view.parent = this;
        return view;
    }

    public boolean isChildView() { return parent != null; }

    /** 沿 parent 链找到根 emitter */
    private AiChatSseEmitter root() {
        AiChatSseEmitter e = this;
        int guard = 0;
        while (e.parent != null && guard++ < 16) {
            e = e.parent;
        }
        return e;
    }

    private final Object closeLock = new Object();

    /**
     * 注册关闭回调。
     *
     * <p><b>修复竞态</b>：入列与"是否已关闭"的判断必须在同一把锁内完成，
     * 且入列后再于锁外执行，避免"回调入列后永远等不到 fire"的窗口。</p>
     */
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
            try { callback.run(); } catch (Exception ignored) {}
        }
    }

    /**
     * 统一关闭入口：底层连接只由根 emitter 真正关闭，子视图仅标记自身结束。
     *
     * @param err 非 null 表示异常结束（completeWithError）
     */
    private void doClose(Throwable err) {
        if (!closed.compareAndSet(false, true)) return;

        // ★ 子视图：不关闭底层连接，只走回调
        if (parent != null) {
            log.debug(">>> SSE 子视图结束（不关闭根连接）");
            synchronized (closeLock) { fireCallbacks(); }
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
            // ★ fireCallbacks 必须在锁内，与 addCloseCallback 的入列互斥
            synchronized (closeLock) {
                fireCallbacks();
            }
        }
    }

    /** 兼容旧 API */
    @Deprecated
    public void setOnCloseCallback(Runnable callback) {
        addCloseCallback(callback);
    }

    /**
     * 是否已关闭。子视图返回根连接的状态，
     * 这样 AgentLoop 能感知"用户已断开"并提前退出循环。
     */
    public boolean isClosed() {
        return parent != null ? root().isClosed() : closed.get();
    }

    /** 触发所有回调（幂等，调用方需持 closeLock） */
    private void fireCallbacks() {
        for (Runnable cb : closeCallbacks) {
            try { cb.run(); } catch (Exception e) {
                log.warn("SSE close callback 异常", e);
            }
        }
        closeCallbacks.clear();
    }

    // ===================== send =====================

    public void send(String chunk) {
        if (muted) return;
        if (chunk == null || chunk.isEmpty()) return;
        if (isClosed()) return;

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
            doClose(null);   // ★ 修复：原实现只标记 closed，连接会挂到 300s 超时
        }
    }

    public void send(String eventName, Object data) {
        if (muted) return;
        if (isClosed()) return;

        String name = (eventName == null || eventName.isEmpty()) ? "message" : eventName;

        // ★ 子视图：吞掉终结帧，过程帧转发给根
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
        doClose(ex);
    }
}
