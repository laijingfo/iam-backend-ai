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

        // ④ 从后往前保留，且保证 assistant(tool_calls) + 后续所有 TOOL 成对
        Deque<Message> kept = new ArrayDeque<>();
        int keptTokens = 0;
        if (history != null) {
            int i = history.size() - 1;
            while (i >= 0) {
                Message m = history.get(i);
                int t = estimator.estimate(m.content());

                // ★ P0-4 修复：遇到 TOOL 时，向前找到最近的 assistant，
                //   把 [assistant, ...所有连续 TOOL] 作为一个原子块处理。
                //   原实现逐个 TOOL 判断前一条是否 assistant，遇到
                //   assistant(tool_calls=[A,B]) + tool(A) + tool(B) 这种结构时
                //   tool(B) 前一条是 tool(A)，会被跳过，导致 assistant 与 TOOL 不配对。
                if (m.role() == MessageRole.TOOL) {
                    // 向前找到最近的 assistant
                    int assistantIdx = i - 1;
                    while (assistantIdx >= 0
                            && history.get(assistantIdx).role() == MessageRole.TOOL) {
                        assistantIdx--;
                    }
                    if (assistantIdx < 0
                            || history.get(assistantIdx).role() != MessageRole.ASSISTANT) {
                        log.warn("[context-trim] TOOL 消息缺少配对 assistant，跳过");
                        i--;   // 单条跳过，继续往前
                        continue;
                    }

                    // 整块 token = assistant + 它后面所有 TOOL
                    int blockTokens = 0;
                    for (int k = assistantIdx; k <= i; k++) {
                        blockTokens += estimator.estimate(history.get(k).content());
                    }
                    if (keptTokens + blockTokens > historyBudget) {
                        // 整块放不下 → 整块丢弃，直接结束（前面的更放不下）
                        break;
                    }
                    // 从后往前 addFirst，保持原顺序
                    for (int k = i; k >= assistantIdx; k--) {
                        kept.addFirst(history.get(k));
                    }
                    keptTokens += blockTokens;
                    i = assistantIdx - 1;   // 跳过整块
                    continue;
                }

                // 普通消息（USER / ASSISTANT 无 tool_calls）
                if (keptTokens + t > historyBudget) break;
                kept.addFirst(m);
                keptTokens += t;
                i--;
            }
        }

// ⑤ 若首条是 assistant，说明配对断裂（它声明的 tool_calls 的 TOOL 被裁掉了），
//    丢弃它；若其后面还跟着 TOOL，也一并丢弃。
//    ★ P0-4：这里改为"循环丢弃直到首条不是 assistant"，
//    且丢弃 assistant 后若紧跟 TOOL，也丢弃（防御性，正常不会出现）。
        while (!kept.isEmpty() && kept.peekFirst().role() == MessageRole.ASSISTANT) {
            Message dropped = kept.pollFirst();
            keptTokens -= estimator.estimate(dropped.content());
            log.debug("[context-trim] 丢弃配对断裂的 assistant 消息");
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