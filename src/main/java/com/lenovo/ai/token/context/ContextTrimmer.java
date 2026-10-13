package com.lenovo.ai.token.context;

import com.lenovo.ai.llm.Message;
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

        // ① system
        if (systemPrompts != null) {
            for (Message sys : systemPrompts) {
                used += estimator.estimate(sys.content());
                result.add(sys);
            }
        }

        // ② 本轮 user
        used += estimator.estimate(currentUser.content());

        // ③ 预留输出空间
        int historyBudget = (int) ((budget - used) * (1 - reserveRatio));
        if (historyBudget < 0) historyBudget = 0;

        // ④ 从后往前保留，且保证 user/assistant 成对
        Deque<Message> kept = new ArrayDeque<>();
        int keptTokens = 0;
        if (history != null) {
            for (int i = history.size() - 1; i >= 0; i--) {
                Message m = history.get(i);
                int t = estimator.estimate(m.content());
                if (keptTokens + t > historyBudget) break;
                kept.addFirst(m);
                keptTokens += t;
            }
        }

        // ⑤ 若首条是 assistant（配对断裂），丢弃它
        if (!kept.isEmpty() && isAssistant(kept.peekFirst())) {
            Message dropped = kept.pollFirst();
            keptTokens -= estimator.estimate(dropped.content());
            log.debug("[context-trim] 丢弃配对断裂的 assistant 消息");
        }

        result.addAll(kept);
        result.add(currentUser);

        log.info("[context-trim] history={} 条 -> 保留 {} 条, 估算 token={} (预算 {})",
                history == null ? 0 : history.size(), kept.size(), used + keptTokens, budget);
        return result;
    }

    private static boolean isAssistant(Message m) {
        // 按项目实际 Message 类型判断，这里示意
        return m.role() != null && "assistant".equalsIgnoreCase(m.role().name());
    }
}