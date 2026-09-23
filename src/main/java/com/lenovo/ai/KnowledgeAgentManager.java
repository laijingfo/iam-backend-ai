package com.lenovo.ai;

import com.lenovo.bean.knowledge.KnowledgeBase;
import com.lenovo.ai.tools.DateTools;
import com.lenovo.ai.tools.KnowledgeAgentTools;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class KnowledgeAgentManager {

    private static final String NO_KB_KEY = "__no_kb__";
    private static final Integer DEFAULT_PROJECT_ID = 79;
    private static final String DEFAULT_IT_CODE = "laijf2";
    private static final String DEFAULT_EMBEDDING = "bge-m3";
    private static final String DEFAULT_INDEX_MODE = "keyword";
    private static final Integer DEFAULT_TOP_K = 3;

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;
    private final KnowledgeAgentTools knowledgeAgentTools;
    private final DateTools dateTools;
    private final KmverseRetrieverFactory retrieverFactory;
    private final KnowledgeTemplateRegistry templateRegistry;
    private final KnowledgeRegistrar knowledgeRegistrar;
    private final Map<String, KnowledgeAssistant> agents = new ConcurrentHashMap<>();

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

    private KnowledgeAssistant buildAssistant(KnowledgeBase knowledgeBase) {
        ChatMemoryProvider memoryProvider = memoryId ->
                MessageWindowChatMemory.withMaxMessages(20);

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