package com.lenovo.ai.llm.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 捕获 LLM 响应，把 cachedTokens 写到 {@code aiMessage.attributes()}。
 *
 * <p><b>为什么不用 ThreadLocal</b>：listener 的 {@code onResponse} 由 LangChain4j
 * 的 HTTP 线程触发，业务侧读取发生在 AgentLoop 线程 —— 两者不是同一线程，
 * ThreadLocal 读不到；且本类是 Spring 单例，多请求并发时还会串台。
 * 写入 {@code aiMessage.attributes()} 后，业务侧从同一次响应对象读取，天然隔离。</p>
 */
@Slf4j
@Component
public class CachedTokenListener implements ChatModelListener {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** attributes 中的 key，供读取方引用 */
    public static final String ATTR_CACHED_TOKENS = "cachedTokens";

    @Override
    public void onResponse(ChatModelResponseContext ctx) {
        try {
            ChatResponse response = ctx.chatResponse();
            if (response == null) {
                return;
            }

            int cached = 0;

            // 路径 1：从 metadata 提取
            Object metadata = response.metadata();
            if (metadata != null) {
                cached = CachedTokenExtractor.extractFromNode(
                        MAPPER.valueToTree(metadata));
            }

            // 路径 2：从 aiMessage 的 attributes 提取（可能已有上游写入）
            if (cached == 0 && response.aiMessage() != null) {
                Object attrs = response.aiMessage().attributes();
                if (attrs != null) {
                    cached = CachedTokenExtractor.extractFromNode(
                            MAPPER.valueToTree(attrs));
                }
            }

            // ★ P0-3 修复：写到 aiMessage.attributes()，业务线程从同一响应对象读
            if (response.aiMessage() != null) {
                AiMessage msg = response.aiMessage();
                try {
                    msg.attributes().put(ATTR_CACHED_TOKENS, cached);
                } catch (UnsupportedOperationException e) {
                    // attributes 不可变时降级：仅打日志，读取方回退到 metadata
                    log.debug("aiMessage.attributes 不可变，cachedTokens={} 不写入", cached);
                }
            }
            log.debug(">>> cachedTokens 捕获: {}", cached);
        } catch (Exception e) {
            log.debug("CachedTokenListener 处理失败", e);
        }
    }

    /** 从 aiMessage.attributes 读取 cachedTokens */
    public static int readFrom(AiMessage msg) {
        if (msg == null || msg.attributes() == null) return 0;
        Object v = msg.attributes().get(ATTR_CACHED_TOKENS);
        return v instanceof Number n ? n.intValue() : 0;
    }
}