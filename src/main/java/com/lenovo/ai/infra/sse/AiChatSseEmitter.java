package com.lenovo.ai.infra.sse;                                                // 包声明

import lombok.extern.slf4j.Slf4j;                                               // 日志
import org.springframework.http.MediaType;                                      // 媒体类型
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;        // SSE 基类

import java.io.IOException;                                                     // IO 异常
import java.util.concurrent.atomic.AtomicBoolean;                               // 原子布尔

@Slf4j                                                                          // 日志
public class AiChatSseEmitter extends SseEmitter {                              // SSE 封装

    /** 连接是否已关闭 */                                                        // 注释
    private final AtomicBoolean closed = new AtomicBoolean(false);              // 关闭标志（原子）

    /** 关闭时的回调，由 runner 注册，用于立即停止 LLM 流 */                        // 注释
    private volatile Runnable onCloseCallback;                                  // 关闭回调

    // ===== 新增：静默 + 抑制关闭 =====
    /** 静默标志：true 时所有 send 被丢弃（不关连接） */                            // 注释
    private volatile boolean muted = false;                                     // 静默标志
    /** 抑制关闭标志：true 时 complete() 不真正关闭连接（内部 Agent 用） */          // 注释
    private volatile boolean suppressComplete = false;                          // 抑制关闭标志

    /**                                                                         // Javadoc
     * 构造：5 分钟超时，注册完成/超时回调。                                       // 说明
     */                                                                         // 结束
    public AiChatSseEmitter() {                                                 // 构造器
        super(300_000L);   // 5 分钟                                            // 超时 5 分钟

        // SseEmitter 生命周期结束（客户端断开、超时、服务端 complete）都会触发   // 注释
        super.onCompletion(() -> {                                              // 完成回调
            closed.set(true);                                                   //    标记关闭
            Runnable cb = onCloseCallback;                                      //    取回调
            if (cb != null) {                                                   //    存在
                try { cb.run(); } catch (Exception ignored) {}                  //    执行（忽略异常）
            }
        });
        super.onTimeout(() -> {                                                 // 超时回调
            closed.set(true);                                                   //    标记关闭
            Runnable cb = onCloseCallback;                                      //    取回调
            if (cb != null) {                                                   //    存在
                try { cb.run(); } catch (Exception ignored) {}                  //    执行（忽略异常）
            }
        });
    }

    /**                                                                         // Javadoc
     * 是否已关闭。                                                               // 说明
     */                                                                         // 结束
    public boolean isClosed() {                                                 // 方法
        return closed.get();                                                    // 返回标志
    }

    /**                                                                         // Javadoc
     * 注册关闭回调。若注册时已关闭，立即执行。                                     // 说明
     */                                                                         // 结束
    public void setOnCloseCallback(Runnable callback) {                         // 方法
        this.onCloseCallback = callback;                                        // 赋值
        // 如果注册之前就已经关闭了，立即回调                                       // 注释
        if (closed.get() && callback != null) {                                 //    已关闭且回调非空
            try { callback.run(); } catch (Exception ignored) {}                //    立即执行
        }
    }

    // ===================== 新增：静默 / 抑制关闭 =====================

    /** 静默：后续 send 全部丢弃（不真正关闭连接）。用于内部 Agent（planner/synthesizer）。 */
    public void mute() { this.muted = true; }

    /** 取消静默。 */
    public void unmute() { this.muted = false; }

    public boolean isMuted() { return muted; }

    /**
     * 抑制 complete()：内部 Agent 跑完不关闭连接。
     * 用于 Orchestrator 里跑子 Agent 时，避免第一个子任务完成就关连接。
     */
    public void setSuppressComplete(boolean b) { this.suppressComplete = b; }

    public boolean isSuppressComplete() { return suppressComplete; }

    // ===================== send =====================

    /** 发送纯文本 delta（事件名为前端约定的 delta） */                              // 注释
    public void send(String chunk) {                                            // 发送文本
        if (muted) return;                                                      // 0. 静默：直接丢弃
        if (chunk == null || chunk.isEmpty()) return;                           // 1. 空返回
        if (closed.get()) return;                                               // 2. 已关闭返回
        try {                                                                   // 3. try
            super.send(SseEmitter.event()                                       //    发送
                    .name("delta")                                              //    事件名 delta
                    .data(chunk, MediaType.TEXT_PLAIN));                        //    文本类型
        } catch (IOException e) {                                               // 4. IO 异常
            log.warn("SSE send failed (IOException): {}", e.getMessage());      //    日志
            closed.set(true);                                                   //    标记关闭
        } catch (IllegalStateException e) {                                     // 5. 状态异常
            log.warn("SSE already closed, ignore send.");                       //    日志
            closed.set(true);                                                   //    标记关闭
        }
    }

    /** 发送带事件名的结构化事件（payload 由 Spring 序列化为 JSON） */                // 注释
    public void send(String eventName, Object data) {                           // 发送事件
        if (muted) return;                                                      // 0. 静默：直接丢弃
        if (closed.get()) return;                                               // 1. 已关闭返回
        try {                                                                   // 2. try
            super.send(SseEmitter.event()                                       //    发送
                    .name(eventName == null || eventName.isEmpty() ? "message" : eventName) // 事件名
                    .data(data, MediaType.APPLICATION_JSON));                   //    JSON 类型
        } catch (IOException e) {                                               // 3. IO 异常
            log.warn("SSE send failed (IOException): {}", e.getMessage());      //    日志
            closed.set(true);                                                   //    标记关闭
        } catch (IllegalStateException e) {                                     // 4. 状态异常
            log.warn("SSE already closed, ignore send.");                       //    日志
            closed.set(true);                                                   //    标记关闭
        }
    }

    /**                                                                         // Javadoc
     * 发送原始 SseEventBuilder。                                                 // 说明
     */                                                                         // 结束
    public void send(SseEmitter.SseEventBuilder event) {                        // 发送
        if (muted) return;                                                      // 0. 静默：直接丢弃
        if (closed.get()) return;                                               // 1. 已关闭返回
        try {                                                                   // 2. try
            super.send(event);                                                  //    发送
        } catch (IOException e) {                                               // 3. IO 异常
            log.warn("SSE send failed (IOException): {}", e.getMessage());      //    日志
            closed.set(true);                                                   //    标记关闭
        } catch (IllegalStateException e) {                                     // 4. 状态异常
            log.warn("SSE already closed, ignore send.");                       //    日志
            closed.set(true);                                                   //    标记关闭
        }
    }

    @Override                                                                   // 重写
    public void complete() {                                                    // 关闭
        // 【新增】抑制关闭：内部 Agent 跑完不真正关闭连接
        if (suppressComplete) {                                                 //    抑制标志为 true
            log.debug(">>> SSE complete() 被抑制（内部 Agent 场景）");            //    日志
            return;                                                             //    直接返回
        }
        if (!closed.compareAndSet(false, true)) return;                         // 1. CAS 保证幂等
        try {                                                                   // 2. try
            super.complete();                                                   //    调用父类关闭
        } finally {                                                             // 3. finally
            if (onCloseCallback != null) {                                      //    有回调
                try { onCloseCallback.run(); } catch (Exception ignored) {}     //    执行
                onCloseCallback = null;                                         //    置空（避免重复执行）
            }
        }
    }
}