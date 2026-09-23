package com.lenovo.ai;

import com.lenovo.bean.knowledge.KnowledgeBase;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import org.springframework.stereotype.Component;

@Component
public class KmverseRetrieverFactory {

    private final KnowledgeService knowledgeService;

    public KmverseRetrieverFactory(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    public ContentRetriever create(KnowledgeBase knowledgeBase) {
        if (knowledgeBase == null) {
            throw new IllegalArgumentException("knowledgeBase 不能为空");
        }
        if (knowledgeBase.getRelation() == null || knowledgeBase.getRelation().isEmpty()) {
            throw new IllegalArgumentException("knowledgeBase.relation 不能为空");
        }
        return new KmverseDocumentRetriever(knowledgeService, knowledgeBase);
    }
}