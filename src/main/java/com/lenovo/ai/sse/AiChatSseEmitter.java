package com.lenovo.ai.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
public class AiChatSseEmitter extends SseEmitter {

    public AiChatSseEmitter() {
        super(120_000L);   // 2 分钟超时；配合 onTimeout 和 async.request-timeout
    }

    /** 发纯文本事件（event: message）。 */
    public void send(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        try {
            super.send(SseEmitter.event()
                    .name("message")
                    .data(chunk, MediaType.TEXT_PLAIN));
        } catch (IOException e) {
            log.warn("SSE send failed: {}", chunk, e);
        } catch (IllegalStateException e) {
            log.warn("SSE already closed: {}", chunk, e);
        }
    }

    /** 发自定义事件（delta / error 等）。 */
    public void send(SseEmitter.SseEventBuilder event) {
        try {
            super.send(event);
        } catch (IOException e) {
            log.warn("SSE send failed", e);
        } catch (IllegalStateException e) {
            log.warn("SSE already closed", e);
        }
    }
}