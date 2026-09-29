package com.lenovo.ai;

import com.lenovo.ai.tools.DateTools;
import com.lenovo.bean.knowledge.KnowledgeBase;
import com.lenovo.ai.tools.KnowledgeAgentTools;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeAgentManager {

    private static final String NO_KB_KEY = "__no_kb__";
    private static final int MEMORY_MAX_MESSAGES = 10;

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;
    private final KnowledgeAgentTools knowledgeAgentTools;
    private final DateTools dateTools;
    private final KmverseRetrieverFactory retrieverFactory;
    private final KnowledgeTemplateRegistry templateRegistry;
    private final KnowledgeRegistrar knowledgeRegistrar;

    private final Map<String, KnowledgeAssistant> agents = new ConcurrentHashMap<>();

    /** ✅ memoryId -> ChatMemory，用于中断时清空历史 */
    private final Map<String, ChatMemory> memoryCache = new ConcurrentHashMap<>();

    public void register(String knowledgeId) {
        knowledgeRegistrar.register(knowledgeId);
        agents.remove(knowledgeId);
    }

    public KnowledgeAssistant getAssistant(String knowledgeId) {
        if (knowledgeId == null || knowledgeId.isBlank()) {
            return agents.computeIfAbsent(NO_KB_KEY, k -> buildAssistant(null));
        }
        KnowledgeBase knowledgeBase = templateRegistry.get(knowledgeId);
        if (knowledgeBase == null) {
            throw new IllegalArgumentException("知识库模板未注册：" + knowledgeId);
        }
        return agents.computeIfAbsent(knowledgeId, k -> buildAssistant(knowledgeBase));
    }

    /**
     * ✅ 清空指定 memoryId 的对话历史。
     * 用于中断（cancelled=true）或出错时，避免不完整的 assistant 消息污染下一次请求。
     */
    public void clearMemory(String memoryId) {
        if (memoryId == null || memoryId.isBlank()) return;
        ChatMemory memory = memoryCache.get(memoryId);
        if (memory != null) {
            try {
                memory.clear();
                log.info(">>> clearMemory 成功, memoryId={}", memoryId);
            } catch (Exception e) {
                log.warn(">>> clearMemory 失败, memoryId={}", memoryId, e);
            }
        } else {
            log.debug(">>> clearMemory 跳过，memoryId={} 无对应 ChatMemory", memoryId);
        }
    }

    private KnowledgeAssistant buildAssistant(KnowledgeBase knowledgeBase) {
        // ✅ 把 ChatMemory 缓存起来，方便后续清空
        ChatMemoryProvider memoryProvider = memoryId ->
                memoryCache.computeIfAbsent(String.valueOf(memoryId), id ->
                        MessageWindowChatMemory.builder()
                                .id(id)
                                .maxMessages(MEMORY_MAX_MESSAGES)
                                .build());

        AiServices<KnowledgeAssistant> builder = AiServices.builder(KnowledgeAssistant.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .tools(knowledgeAgentTools)
                .tools(dateTools)
                .chatMemoryProvider(memoryProvider);

        if (knowledgeBase != null) {
            ContentRetriever retriever = retrieverFactory.create(knowledgeBase);
            builder.contentRetriever(retriever);
        }
        return builder.build();
    }
}