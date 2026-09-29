package com.lenovo.ai.knowledge.tools;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lenovo.ai.knowledge.KnowledgeRegistrar;
import com.lenovo.ai.service.KnowledgeService;
import com.lenovo.ai.knowledge.KnowledgeTemplateRegistry;
import com.lenovo.ai.agent.router.KnowledgeRouter;
import com.lenovo.ai.tools.context.AiToolContext;
import com.lenovo.bean.knowledge.Chunks;
import com.lenovo.bean.knowledge.KnowledgeBase;
import com.lenovo.bean.knowledge.KnowledgeRelation;
import com.lenovo.bean.knowledge.ProjectBase;
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

    private static final int MAX_RESULT_ITEMS = 5;
    private static final int MAX_TEXT_LEN = 800;

    /** ✅ 知识库 ID 最小位数（实际 15 位，留 13 位兼容） */
    private static final int MIN_KB_ID_LENGTH = 15;

    private final KnowledgeService knowledgeService;
    private final KnowledgeTemplateRegistry templateRegistry;
    private final KnowledgeRegistrar knowledgeRegistrar;
    private final KnowledgeRouter knowledgeRouter;

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
        return result.stream()
                .map(p -> "- " + p.getName() + " (内部ID: " + p.getId() + ")")
                .collect(Collectors.joining("\n", "用户 " + effectiveUserId + " 下的工作区：\n", ""));
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

    @Tool("基于知识库进行精准问答。knowledgeBaseId 可选，不传则自动路由到最相关的知识库。")
    public String queryAnswer(
            @P(value = "知识库 ID，可选。不传则自动选择。必须是 13 位及以上纯数字，如 739418572585797。",
                    required = false) Long knowledgeBaseId,
            @P("用户的问题") String question) {
        log.info("[Tool] queryAnswer kb={} q={}", knowledgeBaseId, question);

        // ✅ 拦截无效 ID（如工作区 ID 21408），回落到自动路由
        if (knowledgeBaseId != null && !isValidKnowledgeId(knowledgeBaseId)) {
            log.warn("[Tool] 无效 knowledgeBaseId={}，忽略并走自动路由", knowledgeBaseId);
            knowledgeBaseId = null;
        }

        if (knowledgeBaseId == null) {
            knowledgeBaseId = knowledgeRouter.route(question);
            if (knowledgeBaseId == null) {
                return "无法判断该问题属于哪个知识库，请明确指定知识库 ID。";
            }
            log.info("[Tool] 自动路由到知识库：{}", knowledgeBaseId);
        }
        return queryOne(knowledgeBaseId, question);
    }

    /** ✅ 13 位及以上纯数字，且在 templateRegistry 里能查到 */
    private boolean isValidKnowledgeId(Long id) {
        if (id == null) return false;
        String s = String.valueOf(id);
        if (s.length() < MIN_KB_ID_LENGTH || !s.matches("\\d+")) return false;
        return templateRegistry.get(s) != null;
    }

    private String queryOne(Long knowledgeBaseId, String question) {
        String knowledgeId = String.valueOf(knowledgeBaseId);

        // ✅ 位数校验（内部日志用，不返回给 LLM）
        if (knowledgeId.length() < MIN_KB_ID_LENGTH || !knowledgeId.matches("\\d+")) {
            log.warn("[Tool] queryOne 非法知识库 ID: {}", knowledgeId);
            return "无法定位知识库，请稍后再试。";
        }

        KnowledgeBase template = templateRegistry.get(knowledgeId);
        if (template == null) {
            try {
                knowledgeRegistrar.register(knowledgeId);
                template = templateRegistry.get(knowledgeId);
            } catch (Exception e) {
                log.warn("注册知识库失败: {}", knowledgeId, e);
                return "无法加载该知识库，请稍后再试。";
            }
        }
        if (template == null) {
            return "未找到相关知识库。";
        }

        KnowledgeBase kb = copyTemplate(template);
        kb.setQuery(question);

        if (kb.getProjectId() == null) {
            log.warn("[Tool] queryOne kb={} 缺少 projectId", knowledgeId);
            return "该知识库配置不完整，暂时无法检索。";
        }
        if (kb.getRelation() == null || kb.getRelation().isEmpty()) {
            log.warn("[Tool] queryOne kb={} 缺少 relation", knowledgeId);
            return "该知识库配置不完整，暂时无法检索。";
        }
        if (kb.getRelation().get(0).getEmbedding() == null) {
            log.warn("[Tool] queryOne kb={} 缺少 embedding", knowledgeId);
            return "该知识库配置不完整，暂时无法检索。";
        }
        if (kb.getIndexMode() == null) kb.setIndexMode("vector");
        if (kb.getSimilarityTopK() == null) kb.setSimilarityTopK(10);

        JSONArray arr = knowledgeService.queryAnswer(kb);
        if (arr == null || arr.isEmpty()) {
            return "知识库中未找到相关答案。";
        }

        StringBuilder sb = new StringBuilder();
        int n = Math.min(arr.size(), MAX_RESULT_ITEMS);
        for (int i = 0; i < n; i++) {
            JSONObject o = arr.getJSONObject(i);
            String text = o.getString("text");
            if (text == null || text.isBlank()) continue;
            text = text.replaceAll("<!--.*?-->", "").trim();
            if (text.length() > MAX_TEXT_LEN) text = text.substring(0, MAX_TEXT_LEN) + "...";
            sb.append("- ").append(text).append("\n");
        }
        if (sb.length() == 0) return "知识库中未找到相关答案。";

        // ✅ 日志保留 ID，方便排查
        log.info("[Tool] queryOne kb={} 返回 {} 条, 总长 {}", knowledgeId, n, sb.length());

        // ✅ 返回给 LLM 的内容不带任何 ID
        return "以下是与问题相关的内容：\n" + sb;
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