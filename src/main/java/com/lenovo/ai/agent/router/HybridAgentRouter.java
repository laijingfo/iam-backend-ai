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
    private static final String PLANNER_AGENT = "planner";

    /**
     * 复杂问题长度阈值。
     *
     * <p><b>修复</b>：原值 80 对中文过低——一句普通的中文提问很容易超过 80 字符，
     * 导致几乎所有问题都被判为复杂、全部涌入 planner。
     * 而 planner 路径当时存在静默降级缺陷，结果是"全站无响应"。</p>
     */
    private static final int COMPLEX_LENGTH_THRESHOLD = 200;

    /**
     * 并列 / 递进连接词。
     *
     * <p><b>修复</b>：移除了"先""再""另外""还要""分别"等高频单字/常用词，
     * 仅保留真正表达"多意图并列"的双音节及以上连接词。</p>
     */
    private static final List<String> COMPLEX_KEYWORDS = List.of(
            "并且", "然后", "同时", "以及", "接着", "顺便",
            "另外再", "除此之外", "除此之外还"
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
            Agent planner = agentRegistry.get(PLANNER_AGENT);
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
        return fallback(intent);
    }

    /**
     * 复杂问题判断。
     *
     * <p>保守策略：宁可漏判（走单 Agent），不要误判（走多 Agent）。
     * 判为复杂需<b>同时</b>满足：</p>
     * <ol>
     *   <li>长度超过 {@link #COMPLEX_LENGTH_THRESHOLD}（说明信息量足够大）</li>
     *   <li>且（含多问号 <b>或</b> 含并列/递进连接词）——说明存在多个意图</li>
     * </ol>
     * 仅有长度没有并列信号时，视为单一复杂问句，交给单 Agent 处理更稳。
     */
    private boolean isComplex(String question) {
        if (question == null) return false;
        String q = question.trim();
        if (q.length() <= COMPLEX_LENGTH_THRESHOLD) return false;

        long qmCount = q.chars().filter(c -> c == '？' || c == '?').count();
        boolean multiQuestion = qmCount > 1;

        boolean hasConjunction = false;
        for (String kw : COMPLEX_KEYWORDS) {
            if (q.contains(kw)) {
                hasConjunction = true;
                break;
            }
        }

        boolean complex = multiQuestion || hasConjunction;
        if (complex) {
            log.debug(">>> 复杂问题判定: len={}, 问号数={}, 连接词={}",
                    q.length(), qmCount, hasConjunction);
        }
        return complex;
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

    /**
     * 兜底 Agent。
     *
     * <p><b>修复</b>：原实现直接 {@code registry.get(FALLBACK_AGENT)} 后
     * {@code .name()}，未注册时 NPE。这里显式判空。</p>
     */
    private Agent fallback(String intent) {
        Agent fallback = agentRegistry.get(FALLBACK_AGENT);
        if (fallback == null) {
            log.error(">>> 兜底 Agent[{}] 未注册！请检查 AgentRegistry，当前 intent={}",
                    FALLBACK_AGENT, intent);
            throw new IllegalStateException(
                    "兜底 Agent[" + FALLBACK_AGENT + "] 未注册，路由无法完成");
        }
        log.info(">>> 路由: 兜底 agent={}, intent={}", FALLBACK_AGENT, intent);
        return fallback;
    }

    private String oneLine(String s) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 80 ? t.substring(0, 80) + "..." : t;
    }
}
