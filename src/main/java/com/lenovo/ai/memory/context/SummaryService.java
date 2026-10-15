package com.lenovo.ai.memory.context;

import com.lenovo.ai.llm.LlmClient;
import com.lenovo.ai.llm.Message;
import com.lenovo.ai.memory.util.TextUtils;
import com.lenovo.ai.util.DigestUtils;
import com.lenovo.util.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryService {

    private static final String CACHE_PREFIX = "ai:summary:";
    /** 用于生成缓存 key 的前缀消息数（稳定前缀，避免每次新消息 key 全变） */
    private static final int PREFIX_MESSAGES = 5;
    private static final int PREFIX_MAX_CHARS = 2000;

    private final LlmClient llmClient;
    private final RedisUtils redisUtils;

    public String summarize(String sessionId, List<Message> messages) {
        return summarize(sessionId, messages, 200);
    }

    /**
     * 生成对话摘要。
     *
     * @param sessionId   会话 ID，<b>必传</b>。用于隔离缓存 key，
     *                    避免不同会话前缀相同时串号（不同用户看到别人的摘要）。
     * @param messages    待摘要的历史消息
     * @param maxWordSize 摘要字数上限
     */
    public String summarize(String sessionId, List<Message> messages, int maxWordSize) {
        if (messages == null || messages.isEmpty()) return "";

        String raw = messages.stream()
                .map(m -> m.role() + ": " + m.content())
                .collect(Collectors.joining("\n"));

        // ★ 只用前 N 条消息的 hash 作为缓存 key（前缀稳定）
        String prefix = messages.stream()
                .limit(PREFIX_MESSAGES)
                .map(m -> m.role() + ": " + m.content())
                .collect(Collectors.joining("\n"));
        if (prefix.length() > PREFIX_MAX_CHARS) {
            prefix = prefix.substring(0, PREFIX_MAX_CHARS);
        }

        // ★ P0-1 修复：把 sessionId 纳入 key，避免不同会话前缀相同时跨会话串号。
        String keySource = (sessionId == null ? "" : sessionId) + "|" + prefix;
        String cacheKey = CACHE_PREFIX + DigestUtils.md5(keySource)
                + ":len" + messages.size();

        try {
            Object cached = redisUtils.get(cacheKey);
            if (cached != null) {
                String cachedStr = String.valueOf(cached);
                if (!cachedStr.isBlank() && !TextUtils.looksLikeId(cachedStr)) {
                    log.debug(">>> 使用缓存的摘要：cacheKey={}", cacheKey);
                    return cachedStr;
                }
                log.warn(">>> 缓存内容可疑，忽略：cacheKey={}, value={}",
                        cacheKey, TextUtils.preview(cachedStr));
            }
        } catch (Exception e) {
            log.warn(">>> 读取 Redis 缓存失败", e);
        }

        String prompt = """
            请把以下对话压缩成简洁摘要，保留：
            1. 用户的关键诉求和已确认的事实
            2. 已经执行过的操作及其结果
            3. 尚未解决的问题
            忽略寒暄和重复内容。控制在 %d 字以内，直接输出摘要内容。
            
            对话：
            %s
            """.formatted(maxWordSize, raw);

        String summary = TextUtils.stripCodeFence(llmClient.chatSimple(prompt));
        if (summary == null || summary.isBlank()) {
            log.warn(">>> LLM 摘要返回为空");
            return "";
        }
        if (TextUtils.looksLikeId(summary)) {
            log.warn(">>> LLM 摘要疑似返回了 ID，放弃：{}", TextUtils.preview(summary));
            return "";
        }

        try {
            redisUtils.set(cacheKey, summary, Duration.ofHours(1).toSeconds());
            log.debug(">>> 摘要已生成并缓存：cacheKey={}, summaryLen={}", cacheKey, summary.length());
        } catch (Exception e) {
            log.warn(">>> 保存摘要到 Redis 缓存失败", e);
        }

        return summary;
    }


    /** @deprecated 改用 {@link #summarize(String, List)}，旧签名无会话隔离 */
    @Deprecated
    public String summarize(List<Message> messages) {
        return summarize(null, messages, 200);
    }

    /** @deprecated 改用 {@link #summarize(String, List, int)}，旧签名无会话隔离 */
    @Deprecated
    public String summarize(List<Message> messages, int maxWordSize) {
        return summarize(null, messages, maxWordSize);
    }
}