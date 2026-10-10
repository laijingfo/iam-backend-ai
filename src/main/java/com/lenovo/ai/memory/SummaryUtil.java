package com.lenovo.ai.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lenovo.ai.llm.LlmClient;
import com.lenovo.ai.llm.Message;
import com.lenovo.util.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 对话摘要生成工具类
 * <p>
 * 提供统一的对话摘要生成功能，用于：
 * 1. 长期记忆上下文总结（ContextBuilder）
 * 2. 长期记忆抽取分析（MemoryExtractor）
 * 3. 其他需要对话压缩的场景
 *
 * @author laifo
 * @date 2026-10-10
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryUtil {

    private final LlmClient llmClient;
    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;

    /**
     * 将对话压缩成简洁摘要
     * <p>
     * 功能特点：
     * 1. 保留关键诉求、已确认事实、已执行操作及结果、未解决问题
     * 2. 忽略寒暄和重复内容
     * 3. 默认控制在 200 字以内
     * 4. 支持 Redis 缓存加速（缓存 1 小时）
     *
     * @param messages 待总结的对话消息列表
     * @return 对话摘要
     */
    public String summarize(List<Message> messages) {
        return summarize(messages, 200);
    }

    /**
     * 将对话压缩成简洁摘要（可自定义最大长度）
     *
     * @param messages    待总结的对话消息列表
     * @param maxWordSize 摘要的最大字数限制
     * @return 对话摘要
     */
    public String summarize(List<Message> messages, int maxWordSize) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        // 将消息转换为原始文本
        String raw = messages.stream()
                .map(m -> m.role() + ": " + m.content())
                .collect(Collectors.joining("\n"));

        // 生成缓存 key
        String cacheKey = "ai:summary:" + Integer.toHexString(raw.hashCode());
        
        try {
            // 尝试从缓存读取
            Object cached = redisUtils.get(cacheKey);
            if (cached != null) {
                log.debug(">>> 使用缓存的摘要：cacheKey={}", cacheKey);
                return String.valueOf(cached);
            }
        } catch (Exception e) {
            log.warn(">>> 读取 Redis 缓存失败", e);
        }

        // 调用 LLM 生成摘要
        String prompt = """
            请把以下对话压缩成简洁摘要，保留：
            1. 用户的关键诉求和已确认的事实
            2. 已经执行过的操作及其结果
            3. 尚未解决的问题
            忽略寒暄和重复内容。控制在 %d 字以内，直接输出摘要内容。
            
            对话：
            %s
            """.formatted(maxWordSize, raw);

        String summary = llmClient.chatSimple(prompt);
        
        // 清理可能的 markdown 代码块标记
        summary = stripCodeFence(summary);

        try {
            // 保存到 Redis 缓存（1 小时有效）
            redisUtils.set(cacheKey, summary, Duration.ofHours(1).toSeconds());
            log.debug(">>> 摘要已生成并缓存：cacheKey={}, summaryLen={}", 
                    cacheKey, summary.length());
        } catch (Exception e) {
            log.warn(">>> 保存摘要到 Redis 缓存失败", e);
        }

        return summary;
    }

    /**
     * 从对话中提取值得长期记住的信息项
     * <p>
     * 信息类型包括：
     * - PREFERENCE: 用户偏好（语言、风格、习惯）
     * - FACT: 关于用户的稳定事实（部门、职位、常用系统）
     * - ENTITY: 用户关注的具体对象（项目名、系统名）
     *
     * @param dialog 对话文本
     * @return JSON 格式的提取结果列表
     */
    public String extractLongTermMemories(String dialog) {
        if (dialog == null || dialog.isBlank()) {
            return "[]";
        }

        String prompt = """
            从以下对话中抽取值得长期记住的用户信息。
            只输出 JSON 数组，不要任何解释。格式：
            [{"type":"PREFERENCE|FACT|ENTITY","content":"...","importance":0.0~1.0}]
            没有值得记住的信息则输出 []。
            
            判断标准：
            - PREFERENCE: 用户偏好（语言、风格、习惯）
            - FACT: 关于用户的稳定事实（部门、职位、常用系统）
            - ENTITY: 用户关注的具体对象（项目名、系统名）
            临时性问题不要抽取。
            
            对话：
            %s
            """.formatted(dialog);

        String result = stripCodeFence(llmClient.chatSimple(prompt));
        
        // 验证结果是否为合法的 JSON
        try {
            objectMapper.readValue(result, new TypeReference<List<Map<String, Object>>>() {});
            return result;
        } catch (Exception e) {
            log.warn(">>> 提取的记忆不是合法的 JSON，返回空数组：{}", result);
            return "[]";
        }
    }

    /**
     * 移除 markdown 代码块标记（```json ... ``` 或 ``` ... ```）
     *
     * @param s 原始字符串
     * @return 清理后的字符串
     */
    public String stripCodeFence(String s) {
        if (s == null) {
            return "";
        }
        
        s = s.trim();
        if (s.startsWith("```")) {
            s = s.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
        }
        return s;
    }
}