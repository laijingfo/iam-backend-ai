package com.lenovo.ai;

import com.lenovo.bean.knowledge.KnowledgeBase;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KnowledgeTemplateRegistry {

    private final Map<String, KnowledgeBase> templates = new ConcurrentHashMap<>();

    public void put(String knowledgeId, KnowledgeBase knowledgeBase) {
        if (knowledgeId == null || knowledgeBase == null) return;
        templates.put(knowledgeId, knowledgeBase);
    }

    public KnowledgeBase get(String knowledgeId) {
        return knowledgeId == null ? null : templates.get(knowledgeId);
    }

    public void remove(String knowledgeId) {
        if (knowledgeId != null) templates.remove(knowledgeId);
    }

    public boolean contains(String knowledgeId) {
        return knowledgeId != null && templates.containsKey(knowledgeId);
    }
}