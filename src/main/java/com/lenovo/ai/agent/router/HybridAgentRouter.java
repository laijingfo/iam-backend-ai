package com.lenovo.ai.agent.router;

import com.lenovo.ai.agent.core.Agent;
import com.lenovo.ai.agent.core.AgentRegistry;
import com.lenovo.ai.llm.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 多级路由：
 *   1. 前端显式指定
 *   2. 复杂问题 → planner（多 Agent 编排）
 *   3. 关键词意图匹配（唯一候选直接命中）
 *   4. LLM 在候选中挑选
 *   5. 兜底 general
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HybridAgentRouter implements AgentRouter {

    private static final String FALLBACK_AGENT = "general";
    private static final int COMPLEX_LENGTH_THRESHOLD = 80;
    private static final List<String> COMPLEX_KEYWORDS = List.of(
            "并且", "然后", "同时", "以及", "还要", "另外", "顺便",
            "先", "再", "接着", "分别", "各自"
    );

    private final AgentRegistry agentRegistry;
    private final AgentIntentMatcher intentMatcher;
    private final LlmClient llmClient;

    @Override
    public Agent route(RoutingRequest req) {
        // 1. 显式指定
        if (req.explicitAgent() != null && agentRegistry.contains(req.explicitAgent())) {
            Agent a = agentRegistry.get(req.explicitAgent());
            log.info(">>> 路由: 显式指定 agent={}", a.name());
            return a;
        }

        // 2. 复杂问题 → planner
        if (isComplex(req.question())) {
            Agent planner = agentRegistry.get("planner");
            if (planner != null) {
                log.info(">>> 路由: 复杂问题, agent=planner");
                return planner;
            }
            log.warn(">>> 复杂问题但 planner 未注册，降级到意图匹配");
        }

        // 3. 意图匹配
        String intent = intentMatcher.match(req.question());
        List<Agent> candidates = agentRegistry.byIntent(intent);
        if (candidates.size() == 1) {
            Agent a = candidates.get(0);
            log.info(">>> 路由: 意图命中 intent={}, agent={}", intent, a.name());
            return a;
        }

        // 4. LLM 选（多候选）
        if (candidates.size() > 1) {
            Agent picked = llmPick(req.question(), candidates);
            if (picked != null) {
                log.info(">>> 路由: LLM 选择 agent={}, 候选={}", picked.name(),
                        candidates.stream().map(Agent::name).toList());
                return picked;
            }
        }

        // 5. 兜底
        Agent fallback = agentRegistry.get(FALLBACK_AGENT);
        log.info(">>> 路由: 兜底 agent={}, intent={}", FALLBACK_AGENT, intent);
        return fallback;
    }

    /**
     * 复杂问题判断。保守策略：宁可漏判（走单 Agent），不要误判（走多 Agent）。
     * 命中任一条件即视为复杂：
     *   1. 长度超过阈值
     *   2. 包含并列/递进连接词
     *   3. 包含多个问号
     * 即使误判，Planner 也可能只输出 1 个任务，代价是多一次 LLM 调用。
     */
    private boolean isComplex(String question) {
        if (question == null) return false;
        String q = question.trim();
        if (q.length() > COMPLEX_LENGTH_THRESHOLD) return true;
        long qmCount = q.chars().filter(c -> c == '？' || c == '?').count();
        if (qmCount > 1) return true;
        for (String kw : COMPLEX_KEYWORDS) {
            if (q.contains(kw)) return true;
        }
        return false;
    }

    private Agent llmPick(String question, List<Agent> candidates) {
        try {
            String menu = candidates.stream()
                    .map(a -> "- " + a.name() + ": " + oneLine(a.rolePrompt()))
                    .collect(Collectors.joining("\n"));
            String prompt = """
                用户问题：%s
                可选 Agent：
                %s
                只输出最合适 Agent 的名字（一个单词），不要任何解释。
                """.formatted(question, menu);
            String name = llmClient.chatSimple(prompt).trim();
            String cleaned = name.replaceAll("[^a-zA-Z_]", "");
            return agentRegistry.contains(cleaned) ? agentRegistry.get(cleaned) : null;
        } catch (Exception e) {
            log.warn(">>> 路由 LLM 选择失败，回退兜底", e);
            return null;
        }
    }

    private String oneLine(String s) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 80 ? t.substring(0, 80) + "..." : t;
    }
}