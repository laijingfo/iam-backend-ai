package com.lenovo.ai;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lenovo.bean.knowledge.KnowledgeBase;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class KmverseDocumentRetriever implements ContentRetriever {

    private final KnowledgeService knowledgeService;
    private final KnowledgeBase knowledgeBaseTemplate;

    public KmverseDocumentRetriever(KnowledgeService knowledgeService,
                                    KnowledgeBase knowledgeBaseTemplate) {
        this.knowledgeService = knowledgeService;
        this.knowledgeBaseTemplate = knowledgeBaseTemplate;
    }

    @Override
    public List<Content> retrieve(Query query) {
        log.info(">>> KmverseDocumentRetriever.retrieve, query={}", query.text());

        KnowledgeBase knowledgeBase = copyTemplate();
        knowledgeBase.setQuery(query.text());

        JSONArray data = knowledgeService.queryAnswer(knowledgeBase);
        List<Content> contents = new ArrayList<>();
        if (data == null || data.isEmpty()) {
            log.info(">>> 检索到 0 条内容");
            return contents;
        }

        for (int i = 0; i < data.size(); i++) {
            JSONObject doc = data.getJSONObject(i);
            String text = doc.getString("text");
            if (text == null || text.isBlank()) continue;

            Metadata metadata = new Metadata();
            metadata.put("source", "kmverse");

            JSONObject metadataJson = doc.getJSONObject("metadata");
            if (metadataJson != null) {
                for (String key : metadataJson.keySet()) {
                    if (key == null) continue;
                    Object value = metadataJson.get(key);
                    if (value == null) continue;
                    metadata.put(key, String.valueOf(value));
                }
            }

            contents.add(Content.from(TextSegment.from(text, metadata)));
        }
        log.info(">>> 检索到 {} 条内容", contents.size());
        return contents;
    }

    private KnowledgeBase copyTemplate() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setProjectId(knowledgeBaseTemplate.getProjectId());
        kb.setRelation(knowledgeBaseTemplate.getRelation());
        kb.setIndexMode(knowledgeBaseTemplate.getIndexMode());
        kb.setSimilarityTopK(knowledgeBaseTemplate.getSimilarityTopK());
        kb.setScore(knowledgeBaseTemplate.getScore());
        kb.setContextCompletion(knowledgeBaseTemplate.getContextCompletion());
        kb.setFilenameCompletion(knowledgeBaseTemplate.getFilenameCompletion());
        kb.setWebsearchSize(knowledgeBaseTemplate.getWebsearchSize());
        kb.setInternalSearch(knowledgeBaseTemplate.getInternalSearch());
        kb.setWebsearchMode(knowledgeBaseTemplate.getWebsearchMode());
        kb.setWebUsername(knowledgeBaseTemplate.getWebUsername());
        kb.setWebApikey(knowledgeBaseTemplate.getWebApikey());
        kb.setWebEngine(knowledgeBaseTemplate.getWebEngine());
        kb.setSessionId(knowledgeBaseTemplate.getSessionId());
        kb.setTraceId(knowledgeBaseTemplate.getTraceId());
        kb.setSpanId(knowledgeBaseTemplate.getSpanId());
        kb.setChatbotName(knowledgeBaseTemplate.getChatbotName());
        kb.setRerank(knowledgeBaseTemplate.getRerank());
        kb.setAgent(knowledgeBaseTemplate.getAgent());
        kb.setGlossaryIds(knowledgeBaseTemplate.getGlossaryIds());
        kb.setReturnSummary(knowledgeBaseTemplate.getReturnSummary());
        kb.setRetrievalResultCache(knowledgeBaseTemplate.getRetrievalResultCache());
        kb.setRetrievalResultCacheTtl(knowledgeBaseTemplate.getRetrievalResultCacheTtl());
        kb.setRetrievalResultCacheMode(knowledgeBaseTemplate.getRetrievalResultCacheMode());
        kb.setRetrievalResultCacheQuerySimilarityThreshold(
                knowledgeBaseTemplate.getRetrievalResultCacheQuerySimilarityThreshold());
        kb.setKmResourceForceRefresh(knowledgeBaseTemplate.getKmResourceForceRefresh());
        kb.setUseWorkspaceRetrievalConfig(knowledgeBaseTemplate.getUseWorkspaceRetrievalConfig());
        kb.setKnowledgeBaseId(knowledgeBaseTemplate.getKnowledgeBaseId());
        return kb;
    }
}