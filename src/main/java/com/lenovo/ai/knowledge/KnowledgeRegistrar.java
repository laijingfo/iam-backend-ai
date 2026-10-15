package com.lenovo.ai.knowledge;

import com.lenovo.ai.service.KnowledgeService;
import com.lenovo.bean.knowledge.KnowledgeBase;
import com.lenovo.bean.knowledge.KnowledgeRelation;
import com.lenovo.bean.knowledge.ProjectBase;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    private static final String DEFAULT_INDEX_MODE = "vector";

    // ★ R7 修复：与 KnowledgeRouter 使用同一份配置，避免换环境时一个能查到、一个查不到。
    @Value("${ai.knowledge-router.project-id:79}")
    private Integer projectId;

    @Value("${ai.knowledge-router.it-code:laijf2}")
    private String itCode;

    @Value("${ai.knowledge-router.default-embedding:Qwen3-Embedding-0.6B}")
    private String defaultEmbedding;

    @Value("${ai.knowledge-router.default-top-k:10}")
    private Integer defaultTopK;

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
        projectBase.setProjectId(projectId);
        projectBase.setItCode(itCode);

        List<Long> knowledgeBaseIds = knowledgeService.queryKnowledgeBase(projectBase);
        if (knowledgeBaseIds == null || !knowledgeBaseIds.contains(knowledgeBaseId)) {
            throw new IllegalArgumentException(
                    "知识库 " + knowledgeBaseId + " 不属于项目 " + projectBase.getProjectId());
        }

        KnowledgeRelation relation = new KnowledgeRelation();
        relation.setKnowledgeBaseId(knowledgeBaseId);
        relation.setEmbedding(defaultEmbedding);

        List<KnowledgeRelation> relationList = new ArrayList<>();
        relationList.add(relation);

        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setProjectId(projectId);
        knowledgeBase.setRelation(relationList);
        knowledgeBase.setSimilarityTopK(defaultTopK);
        knowledgeBase.setIndexMode(DEFAULT_INDEX_MODE);

        templateRegistry.put(knowledgeId, knowledgeBase);
    }
}