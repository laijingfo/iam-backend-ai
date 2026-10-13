package com.lenovo.ai.token;

import com.lenovo.ai.llm.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-12 17:27
 * @project iam-backend-ai
 * @description
 * 上下文裁剪器
 * 策略: 保留 system + 最近 N 轮, 丢弃过老的历史
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextTrimmer {

    /** 默认单次 prompt 上限 */
    public static final int DEFAULT_MAX_TOKENS = 6000;

    private final TokenEstimator estimator;

    /**
     * 裁剪历史对话
     * @param systemPrompts 必须保留的 system 消息 (可为空)
     * @param history       历史消息 (按时间正序)
     * @param currentUser   本轮 user message (必须保留)
     * @param maxTokens     最大 token 数
     * @return 裁剪后的完整 messages (system + history' + user)
     */
    public List<Message> trim(List<Message> systemPrompts,
                              List<Message> history,
                              Message currentUser,
                              int maxTokens) {
        List<Message> result = new ArrayList<>();

        // ① 累加 system 消耗
        int tokens = 0;
        if (systemPrompts != null) {
            for (Message sys : systemPrompts) {
                tokens += estimator.estimate(sys.content());
                result.add(sys);
            }
        }

        // ② 保留本轮 user
        tokens += estimator.estimate(currentUser.content());

        // ③ 预留 20% 给 AI 输出
        int historyBudget = (int) ((maxTokens - tokens) * 0.8);
        if (historyBudget < 0) historyBudget = 0;

        // ④ 从后往前选历史
        List<Message> kept = new ArrayList<>();
        int used = 0;
        if (history != null && !history.isEmpty()) {
            for (int i = history.size() - 1; i >= 0; i--) {
                Message m = history.get(i);
                int t = estimator.estimate(m.content());
                if (used + t > historyBudget) break;
                kept.add(0, m);
                used += t;
            }
        }

        result.addAll(kept);
        result.add(currentUser);

        log.info("[context-trim] 原始历史={} 条, 保留={} 条, 估算 token={}",
                history == null ? 0 : history.size(), kept.size(), tokens + used);

        return result;
    }
}