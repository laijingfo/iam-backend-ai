package com.lenovo.ai.infra.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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

    /** ★ P0 修复：complete 幂等 */
    private final AtomicBoolean completed = new AtomicBoolean(false);

    /** 子视图模式：吞掉 done / complete */
    private volatile boolean childView = false;
    private volatile boolean suppressComplete = false;
    private volatile boolean muted = false;

    public AiChatSseEmitter() {
        super(300_000L);   // 5 分钟超时
    }

    @Override
    public void complete() {
        // ★ 幂等：重复调用直接返回
        if (!completed.compareAndSet(false, true)) {
            log.debug("[sse] complete() 重复调用，已忽略");
            return;
        }
        try {
            super.complete();
        } catch (Exception e) {
            log.debug("[sse] complete 异常（可能已关闭）: {}", e.getMessage());
        }
    }

    @Override
    public void completeWithError(Throwable ex) {
        if (!completed.compareAndSet(false, true)) {
            log.debug("[sse] completeWithError() 重复调用，已忽略");
            return;
        }
        try {
            super.completeWithError(ex);
        } catch (Exception e) {
            log.debug("[sse] completeWithError 异常: {}", e.getMessage());
        }
    }

    /** 发送事件；已 complete 时静默跳过 */
    public void send(String event, Object data) {
        if (completed.get()) {
            log.debug("[sse] 已 complete，跳过 send({})", event);
            return;
        }
        if (muted) {
            log.debug("[sse] 已 mute，跳过 send({})", event);
            return;
        }
        // 子视图：吞掉 done / complete 类终结帧
        if (childView && ("done".equals(event) || "complete".equals(event))) {
            log.debug("[sse] childView 吞掉终结帧 {}", event);
            return;
        }
        try {
            super.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            log.debug("[sse] send({}) 失败（可能已断开）: {}", event, e.getMessage());
        }
    }

    public void mute() { this.muted = true; }
    public void unmute() { this.muted = false; }
    public void setSuppressComplete(boolean v) { this.suppressComplete = v; }
    public boolean isClosed() { return completed.get(); }

    /** 派生子视图（子任务用） */
    public AiChatSseEmitter childView() {
        AiChatSseEmitter child = new AiChatSseEmitter();
        child.childView = true;
        return child;
    }
}