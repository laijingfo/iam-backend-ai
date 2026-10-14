package com.lenovo.ai.token.context;

import com.lenovo.ai.llm.Message;
import com.lenovo.ai.llm.enums.MessageRole;
import com.lenovo.ai.token.config.TokenProperties;
import com.lenovo.ai.token.estimator.TokenEstimator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContextTrimmer {

    /** system 最多占预算的比例，防止 system 挤爆 */
    private static final double SYSTEM_MAX_RATIO = 0.5;

    private final TokenEstimator estimator;
    private final TokenProperties properties;

    public List<Message> trim(List<Message> systemPrompts,
                              List<Message> history,
                              Message currentUser,
                              int maxTokens) {
        int budget = maxTokens > 0 ? maxTokens : properties.getMaxPromptTokens();
        double reserveRatio = properties.getContextReserveRatio();

        List<Message> result = new ArrayList<>();
        int used = 0;

        // ① system：受 SYSTEM_MAX_RATIO 约束，超限时按顺序截断
        int systemBudget = (int) (budget * SYSTEM_MAX_RATIO);
        int systemUsed = 0;
        if (systemPrompts != null) {
            for (Message sys : systemPrompts) {
                int t = estimator.estimate(sys.content());
                if (systemUsed + t > systemBudget) {
                    log.warn("[context-trim] system 超预算，丢弃后续 system 消息: " +
                                    "已用={}, 上限={}, 本条={}",
                            systemUsed, systemBudget, t);
                    break;
                }
                result.add(sys);
                systemUsed += t;
            }
        }
        used += systemUsed;

        // ② 本轮 user：必须保留，超预算则告警
        int userTokens = estimator.estimate(currentUser.content());
        used += userTokens;

        // ③ 预留输出空间
        int historyBudget = (int) ((budget - used) * (1 - reserveRatio));
        if (historyBudget < 0) {
            log.warn("[context-trim] system+user 已超预算: used={}, budget={}，" +
                    "历史消息将全部丢弃", used, budget);
            historyBudget = 0;
        }

        // ④ 从后往前保留，且保证 assistant(tool_calls) + tool 成对
        Deque<Message> kept = new ArrayDeque<>();
        int keptTokens = 0;
        if (history != null) {
            for (int i = history.size() - 1; i >= 0; i--) {
                Message m = history.get(i);
                int t = estimator.estimate(m.content());

                // 若本条是 TOOL，且其配对的 assistant 也要一起保留
                if (m.role() == MessageRole.TOOL) {
                    // TOOL 前面必须紧跟 assistant(tool_calls)
                    if (i == 0 || history.get(i - 1).role() != MessageRole.ASSISTANT) {
                        log.warn("[context-trim] TOOL 消息缺少配对 assistant，跳过");
                        continue;
                    }
                    Message pair = history.get(i - 1);
                    int pairTokens = t + estimator.estimate(pair.content());
                    if (keptTokens + pairTokens > historyBudget) break;
                    kept.addFirst(m);
                    kept.addFirst(pair);
                    keptTokens += pairTokens;
                    i--; // 跳过已处理的 assistant
                    continue;
                }

                if (keptTokens + t > historyBudget) break;
                kept.addFirst(m);
                keptTokens += t;
            }
        }

        // ⑤ 若首条是 assistant（配对断裂），丢弃它及其后紧跟的 TOOL
        while (!kept.isEmpty() && kept.peekFirst().role() == MessageRole.ASSISTANT) {
            Message dropped = kept.pollFirst();
            keptTokens -= estimator.estimate(dropped.content());
            log.debug("[context-trim] 丢弃配对断裂的 assistant 消息");
            // 丢弃后若紧跟 TOOL，也一并丢弃
            while (!kept.isEmpty() && kept.peekFirst().role() == MessageRole.TOOL) {
                Message tool = kept.pollFirst();
                keptTokens -= estimator.estimate(tool.content());
            }
        }

        result.addAll(kept);
        result.add(currentUser);

        log.info("[context-trim] history={} 条 -> 保留 {} 条, 估算 token={} (预算 {})",
                history == null ? 0 : history.size(), kept.size(), used + keptTokens, budget);
        return result;
    }
}