package com.lenovo.ai.llm.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 捕获 LLM 响应，把 cachedTokens 缓存到 ThreadLocal。
 * <p>调用方在同一个线程内读取，用完必须 clear。</p>
 *
 * <p><b>注意</b>：LangChain4j 的 {@code TokenUsage} 不暴露 cachedTokens，
 * 只能通过 listener 抓原始 metadata。</p>
 */
@Slf4j
@Component
public class CachedTokenListener implements ChatModelListener {

    private static final ThreadLocal<Integer> HOLDER = new ThreadLocal<>();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void onResponse(ChatModelResponseContext ctx) {
        try {
            ChatResponse response = ctx.chatResponse();
            if (response == null) {
                HOLDER.set(0);
                return;
            }

            int cached = 0;

            // 路径 1：从 metadata 提取
            Object metadata = response.metadata();
            if (metadata != null) {
                cached = CachedTokenExtractor.extractFromNode(
                        MAPPER.valueToTree(metadata));
            }

            // 路径 2：从 aiMessage 的 attributes 提取
            if (cached == 0 && response.aiMessage() != null) {
                Object attrs = response.aiMessage().attributes();
                if (attrs != null) {
                    cached = CachedTokenExtractor.extractFromNode(
                            MAPPER.valueToTree(attrs));
                }
            }

            HOLDER.set(cached);
            log.debug(">>> cachedTokens 捕获: {}", cached);
        } catch (Exception e) {
            log.debug("CachedTokenListener 处理失败", e);
            HOLDER.set(0);
        }
    }

    /** 读取并清除（必须调用，避免 ThreadLocal 泄漏） */
    public static int getAndClear() {
        Integer v = HOLDER.get();
        HOLDER.remove();
        return v == null ? 0 : v;
    }

    /** 仅读取（不清除，调试用） */
    public static int peek() {
        Integer v = HOLDER.get();
        return v == null ? 0 : v;
    }
}