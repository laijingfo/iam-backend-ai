package com.lenovo.ai;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lenovo.bean.knowledge.Chunks;
import com.lenovo.bean.knowledge.KnowledgeBase;
import com.lenovo.bean.knowledge.KnowledgeUpload;
import com.lenovo.bean.knowledge.ProjectBase;

import java.util.List;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-09-17 09:25
 * @project iam-backend
 * @description 知识库
 */
public interface KnowledgeService {

    JSONArray queryAnswer(KnowledgeBase knowledgeBase);

    List<ProjectBase> queryProject(ProjectBase projectBase);

    List<Long> queryKnowledgeBase(ProjectBase projectBase);

    String uploadKnowledge(KnowledgeUpload knowledgeUpload);

    JSONObject getPageChunks(Chunks chunks);

    JSONObject getDocuments(String knowledgeBaseId);

//    KnowledgeBase getKnowledgeBaseById(Long );
}
