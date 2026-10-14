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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
/**
 * 对话摘要服务：把一段历史消息压缩成简洁摘要。
 *
 * @author laifo
 * @date 2026-10-10
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryService {

    private static final String CACHE_PREFIX = "ai:summary:";

    private final LlmClient llmClient;
    private final RedisUtils redisUtils;

    public String summarize(List<Message> messages) {
        return summarize(messages, 200);
    }

    public String summarize(List<Message> messages, int maxWordSize) {
        if (messages == null || messages.isEmpty()) return "";

        // ★ 用消息数量 + 内容 hash 的组合，避免每次 sublist 变化导致 key 全变
        String raw = messages.stream()
                .map(m -> m.role() + ": " + m.content())
                .collect(Collectors.joining("\n"));

        // 只对前 N 条做 hash（前缀稳定）
        int prefixLen = Math.min(raw.length(), 2000);
        String cacheKey = CACHE_PREFIX + DigestUtils.md5(raw.substring(0, prefixLen));

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

}