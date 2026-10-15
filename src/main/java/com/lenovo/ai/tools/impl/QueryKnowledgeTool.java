package com.lenovo.ai.tools.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lenovo.ai.agent.router.KnowledgeRouter;
import com.lenovo.ai.knowledge.KnowledgeRegistrar;
import com.lenovo.ai.knowledge.KnowledgeTemplateRegistry;
import com.lenovo.ai.service.KnowledgeService;
import com.lenovo.ai.tools.AiTool;
import com.lenovo.ai.tools.ToolResult;
import com.lenovo.ai.tools.context.AiToolContext;
import com.lenovo.bean.knowledge.KnowledgeBase;
import com.lenovo.bean.knowledge.KnowledgeRelation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-09-30 16:18
 * @project iam-backend-ai
 * @description
 * 知识库精准问答工具。
 * knowledgeBaseId 可选：不传时由 KnowledgeRouter 自动路由到最相关的知识库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryKnowledgeTool implements AiTool {

    private static final int MIN_KB_ID_LENGTH = 13;
    private static final int MAX_RESULT_ITEMS = 5;
    /** 首次检索无结果时的扩大召回条数 */
    private static final int RETRY_TOP_K = 30;
    private static final int MAX_TEXT_LEN = 800;

    private final KnowledgeService knowledgeService;
    private final KnowledgeTemplateRegistry templateRegistry;
    private final KnowledgeRegistrar knowledgeRegistrar;
    private final KnowledgeRouter knowledgeRouter;

    @Override
    public String name() { return "query_knowledge"; }

    @Override
    public String description() {
        // ★ 字符串内使用中文引号 “ ”，避免未转义的英文双引号截断 Java 字符串
        return "基于企业知识库进行精准问答。**凡是涉及企业内部制度、流程、角色职责、"
                + "业务术语定义、规范标准、审批规则、操作指引的问题，都必须调用本工具**，"
                + "例如“审核人是谁”“审批流程是什么”“xx 的规定”“怎么申请 xx”。"
                + "不要凭常识回答，也不要要求用户补充信息——先查，查不到再说明。"
                + "除非用户明确给出 13 位及以上纯数字的知识库 ID，否则不要传 knowledgeBaseId，系统会自动路由。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "knowledgeBaseId", Map.of(
                                "type", "string",
                                "description", "知识库 ID（13 位及以上纯数字），可选。不传则自动路由。"),
                        "question", Map.of(
                                "type", "string",
                                "description", "用户的问题")
                ),
                "required", List.of("question")
        );
    }

    @Override
    public Set<String> allowedRoles() { return Set.of("*"); }

    @Override
    public ToolResult execute(Map<String, Object> args, AiToolContext ctx) {
        String question = String.valueOf(args.get("question"));
        if (question == null || question.isBlank() || "null".equals(question)) {
            return ToolResult.fail("问题不能为空");
        }

        Long kbId = parseKbId(args.get("knowledgeBaseId"));
        if (kbId != null && !isValidFormat(kbId)) {
            log.warn("[query_knowledge] knowledgeBaseId 格式非法={}，回落自动路由", kbId);
            kbId = null;
        }
        // ★ 不再检查 templateRegistry，交给 queryOne 懒加载

        if (kbId == null) {
            // ★ 兜底统一由 KnowledgeRouter.route() 处理（单库直返 / 默认库 / 最优库）。
            //   走到这里的 null 意味着"既没向量缓存、又没配默认库"，属配置问题。
            kbId = knowledgeRouter.route(question);
            if (kbId == null) {
                log.error("[query_knowledge] 知识库路由失败！"
                        + "请检查 ai.knowledge-router.project-id / default-kb-id 配置，"
                        + "并调用 KnowledgeRouter.diagnose() 查看状态");
                return ToolResult.ok("知识库服务暂不可用（路由未就绪），请稍后重试。");
            }
            log.info("[query_knowledge] 自动路由到知识库 {}", kbId);
        }

        String content = queryOne(kbId, question, null);

        // ★ 空结果重试：短问题（如"审核人是谁"）向量表征弱，
        //   首次 topK 偏小容易漏召回。扩大召回再试一次，能救回不少案例。
        if (isMiss(content)) {
            log.info("[query_knowledge] 首次检索无结果，扩大召回重试: kb={}, q={}", kbId, question);
            String retry = queryOne(kbId, question, RETRY_TOP_K);
            if (!isMiss(retry)) {
                content = retry;
            }
        }
        return ToolResult.ok(content, Map.of("knowledgeBaseId", kbId));
    }

    /** 只校验格式：13 位及以上纯数字 */
    private boolean isValidFormat(Long id) {
        if (id == null) return false;
        String s = String.valueOf(id);
        return s.length() >= MIN_KB_ID_LENGTH && s.matches("\\d+");
    }

    private Long parseKbId(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }

    private String queryOne(Long knowledgeBaseId, String question, Integer overrideTopK) {
        String knowledgeId = String.valueOf(knowledgeBaseId);
        if (knowledgeId.length() < MIN_KB_ID_LENGTH || !knowledgeId.matches("\\d+")) {
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
        if (template == null) return "未找到相关知识库。";

        KnowledgeBase kb = copyTemplate(template);
        kb.setQuery(question);

        if (kb.getProjectId() == null
                || kb.getRelation() == null || kb.getRelation().isEmpty()
                || kb.getRelation().get(0).getEmbedding() == null) {
            log.warn("[query_knowledge] kb={} 配置不完整", knowledgeId);
            return "该知识库配置不完整，暂时无法检索。";
        }
        if (kb.getIndexMode() == null) kb.setIndexMode("vector");
        if (overrideTopK != null) {
            kb.setSimilarityTopK(overrideTopK);
        } else if (kb.getSimilarityTopK() == null) {
            kb.setSimilarityTopK(10);
        }

        JSONArray arr = knowledgeService.queryAnswer(kb);
        if (arr == null || arr.isEmpty()) return "知识库中未找到相关答案。";

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
        return "以下是与问题相关的内容：\n" + sb;
    }

    /**
     * 判断检索结果是否为"没查到"。
     * <p>集中判断，避免各处硬编码字符串比较。</p>
     */
    private boolean isMiss(String content) {
        if (content == null) return true;
        String c = content.trim();
        if (c.isEmpty()) return true;
        return c.startsWith("知识库中未找到")
                || c.startsWith("无法定位")
                || c.startsWith("无法加载")
                || c.startsWith("未找到相关")
                || c.startsWith("该知识库配置不完整");
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
            List<KnowledgeRelation> rs = new ArrayList<>();
            for (KnowledgeRelation r : src.getRelation()) {
                KnowledgeRelation nr = new KnowledgeRelation();
                nr.setKnowledgeBaseId(r.getKnowledgeBaseId());
                nr.setEmbedding(r.getEmbedding());
                nr.setFilter(r.getFilter());
                nr.setTags(r.getTags());
                nr.setFolders(r.getFolders());
                nr.setFilterLogic(r.getFilterLogic());
                rs.add(nr);
            }
            kb.setRelation(rs);
        }
        return kb;
    }
}
