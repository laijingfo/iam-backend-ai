package com.lenovo.ai;

import com.lenovo.bean.knowledge.KnowledgeBase;
import com.lenovo.bean.knowledge.KnowledgeRelation;
import com.lenovo.bean.knowledge.ProjectBase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-09-23 15:22
 * @project iam-backend-ai
 * @description
 */
@Component
@RequiredArgsConstructor
public class KnowledgeRegistrar {

    private final KnowledgeService knowledgeService;
    private final KnowledgeTemplateRegistry templateRegistry;

    private static final Integer DEFAULT_PROJECT_ID = 79;
    private static final String DEFAULT_IT_CODE = "laijf2";
    private static final String DEFAULT_EMBEDDING = "bge-m3";
    private static final String DEFAULT_INDEX_MODE = "keyword";
    private static final Integer DEFAULT_TOP_K = 3;

    public void register(String knowledgeId) {
        if (knowledgeId == null || knowledgeId.isBlank()) return;
        if (templateRegistry.get(knowledgeId) != null) return;   // 已注册

        Long knowledgeBaseId;
        try {
            knowledgeBaseId = Long.parseLong(knowledgeId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("knowledgeId 不是合法数字：" + knowledgeId);
        }

        ProjectBase projectBase = new ProjectBase();
        projectBase.setProjectId(DEFAULT_PROJECT_ID);
        projectBase.setItCode(DEFAULT_IT_CODE);

        List<Long> knowledgeBaseIds = knowledgeService.queryKnowledgeBase(projectBase);
        if (knowledgeBaseIds == null || !knowledgeBaseIds.contains(knowledgeBaseId)) {
            throw new IllegalArgumentException(
                    "知识库 " + knowledgeBaseId + " 不属于项目 " + projectBase.getProjectId());
        }

        KnowledgeRelation relation = new KnowledgeRelation();
        relation.setKnowledgeBaseId(knowledgeBaseId);
        relation.setEmbedding(DEFAULT_EMBEDDING);

        List<KnowledgeRelation> relationList = new ArrayList<>();
        relationList.add(relation);

        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setProjectId(DEFAULT_PROJECT_ID);
        knowledgeBase.setRelation(relationList);
        knowledgeBase.setSimilarityTopK(DEFAULT_TOP_K);
        knowledgeBase.setIndexMode(DEFAULT_INDEX_MODE);

        templateRegistry.put(knowledgeId, knowledgeBase);
    }
}