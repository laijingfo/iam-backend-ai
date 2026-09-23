package com.lenovo.ai.tools;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lenovo.bean.knowledge.Chunks;
import com.lenovo.bean.knowledge.KnowledgeBase;
import com.lenovo.bean.knowledge.KnowledgeRelation;
import com.lenovo.bean.knowledge.ProjectBase;
import com.lenovo.ai.KnowledgeService;
import com.lenovo.ai.KnowledgeTemplateRegistry;
import com.lenovo.ai.tools.context.AiToolContext;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component("knowledgeAgentTools")
@RequiredArgsConstructor
public class KnowledgeAgentTools {

    private final KnowledgeService knowledgeService;
    private final KnowledgeTemplateRegistry templateRegistry;

    @Tool("查询指定用户下的所有工作区。返回工作区 ID 和名称。userId 不传则使用当前登录用户。")
    public String listProjects(
            @P(value = "用户 ID，通常是 itCode，不传则用当前登录用户", required = false) String userId) {
        String effectiveUserId = (userId == null || userId.isBlank())
                ? currentUserId()
                : userId;
        log.info("[Tool] listProjects userId={}", effectiveUserId);

        ProjectBase base = new ProjectBase();
        base.setItCode(effectiveUserId);
        List<ProjectBase> result = knowledgeService.queryProject(base);
        if (result.isEmpty()) return "未查询到工作区。";
        String body = result.stream()
                .map(p -> "- ID: " + p.getId() + ", 名称: " + p.getName())
                .collect(Collectors.joining("\n", "用户 " + effectiveUserId + " 下的工作区：\n", ""));
        return body;
    }

    @Tool("查询指定工作区下的所有知识库。返回知识库 ID 列表。")
    public String listKnowledgeBases(@P("工作区 ID") Integer projectId) {
        log.info("[Tool] listKnowledgeBases projectId={}", projectId);
        ProjectBase base = new ProjectBase();
        base.setProjectId(projectId);
        base.setItCode(currentUserId());
        List<Long> ids = knowledgeService.queryKnowledgeBase(base);
        if (ids == null || ids.isEmpty()) return "未查询到知识库。";

        StringBuilder sb = new StringBuilder("工作区 " + projectId + " 下的知识库：\n");
        for (Long id : ids) {
            sb.append("- ID: ").append(id).append("\n");
        }
        return sb.toString();
    }

    @Tool("获取指定知识库下的所有文档列表。返回文档 ID 和标题。")
    public String listDocuments(@P("知识库 ID") String knowledgeBaseId) {
        log.info("[Tool] listDocuments knowledgeBaseId={}", knowledgeBaseId);
        JSONObject resp = knowledgeService.getDocuments(knowledgeBaseId);
        JSONArray arr = resp.getJSONArray("result");
        if (arr == null) arr = resp.getJSONArray("data");
        if (arr == null || arr.isEmpty()) return "该知识库下没有文档。";

        StringBuilder sb = new StringBuilder("知识库 " + knowledgeBaseId + " 下的文档：\n");
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.getJSONObject(i);
            JSONArray rows = o.getJSONArray("rows");
            if (rows == null || rows.isEmpty()) continue;
            for (int j = 0; j < rows.size(); j++) {
                JSONObject row = rows.getJSONObject(j);
                sb.append("- ID: ").append(row.getString("id"))
                        .append(", 标题: ").append(row.getString("originName"))
                        .append("\n");
            }
        }
        return sb.toString();
    }

    @Tool("分页查询指定文档的片段。传入文档 ID、关键词（可空）、页码和每页数量。")
    public String getPageChunks(
            @P("文档 ID") String docId,
            @P(value = "关键词模糊检索，可空", required = false) String keyword,
            @P("当前页，从 1 开始") long page,
            @P("每页数量") long pageSize) {
        log.info("[Tool] getPageChunks docId={} keyword={} page={} size={}",
                docId, keyword, page, pageSize);
        Chunks chunks = new Chunks();
        chunks.setDocId(docId);
        chunks.setKeyword(keyword);
        chunks.setPage(page);
        chunks.setPageSize(pageSize);
        JSONObject resp = knowledgeService.getPageChunks(chunks);
        return resp == null ? "未查询到片段。" : resp.toJSONString();
    }

    @Tool("基于知识库进行精准问答，直接返回知识库匹配的答案。适用于已有明确知识库 ID 的场景。")
    public String queryAnswer(
            @P("知识库 ID") Long knowledgeBaseId,
            @P("用户的问题") String question) {
        log.info("[Tool] queryAnswer kb={} q={}", knowledgeBaseId, question);

        KnowledgeBase template = templateRegistry.get(String.valueOf(knowledgeBaseId));
        if (template == null) {
            return "未找到知识库：" + knowledgeBaseId;
        }

        KnowledgeBase kb = copyTemplate(template);
        kb.setQuery(question);

        if (kb.getProjectId() == null) {
            return "知识库 " + knowledgeBaseId + " 缺少 projectId，无法检索。";
        }
        if (kb.getRelation() == null || kb.getRelation().isEmpty()) {
            return "知识库 " + knowledgeBaseId + " 缺少 relation，无法检索。";
        }
        if (kb.getRelation().get(0).getEmbedding() == null) {
            return "知识库 " + knowledgeBaseId + " 缺少 embedding，无法检索。";
        }
        if (kb.getIndexMode() == null) kb.setIndexMode("vector");
        if (kb.getSimilarityTopK() == null) kb.setSimilarityTopK(10);

        JSONArray arr = knowledgeService.queryAnswer(kb);
        if (arr == null || arr.isEmpty()) return "知识库中未找到相关答案。";

        StringBuilder sb = new StringBuilder("知识库返回的答案：\n");
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.getJSONObject(i);
            sb.append("- ").append(o.toJSONString()).append("\n");
        }
        return sb.toString();
    }

    private String currentUserId() {
        AiToolContext ctx = AiToolContext.get();
        return ctx == null ? "laijf2" : ctx.getUserId();
    }



    private KnowledgeBase copyTemplate(KnowledgeBase src) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setProjectId(src.getProjectId());
        kb.setIndexMode(src.getIndexMode());
        kb.setSimilarityTopK(src.getSimilarityTopK());
        kb.setScore(src.getScore());
        kb.setRerank(src.getRerank());
        kb.setKnowledgeBaseId(src.getKnowledgeBaseId());

        if (src.getRelation() != null) {
            List<KnowledgeRelation> relations = new ArrayList<>();
            for (KnowledgeRelation r : src.getRelation()) {
                KnowledgeRelation nr = new KnowledgeRelation();
                nr.setKnowledgeBaseId(r.getKnowledgeBaseId());
                nr.setEmbedding(r.getEmbedding());
                nr.setFilter(r.getFilter());
                nr.setTags(r.getTags());
                nr.setFolders(r.getFolders());
                nr.setFilterLogic(r.getFilterLogic());
                relations.add(nr);
            }
            kb.setRelation(relations);
        }
        return kb;
    }
}