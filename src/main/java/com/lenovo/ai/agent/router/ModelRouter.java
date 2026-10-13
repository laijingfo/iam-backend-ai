package com.lenovo.ai.agent.router;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-12 17:31
 * @project iam-backend-ai
 * @description
 * 模型路由器
 * 根据 Agent / 问题复杂度选择模型
 */
@Slf4j
@Component
public class ModelRouter {

    public static final String SMALL = "qwen-turbo";
    public static final String MEDIUM = "kimi-k2.6";
    public static final String LARGE = "qwen-max";

    /**
     * 选择模型
     * @param agentName     Agent 名 (planner / knowledge / ...)
     * @param message       用户消息
     * @param historySize   历史消息数
     */
    public String select(String agentName, String message, int historySize) {
        // 规则 1: planner 复杂推理, 用大模型
        if ("planner".equalsIgnoreCase(agentName)) {
            log.debug("[model-route] planner → LARGE");
            return LARGE;
        }

        // 规则 2: 极短消息 (打招呼、确认) → 小模型
        if (message != null && message.length() < 15 && historySize < 3) {
            log.debug("[model-route] 短消息 → SMALL");
            return SMALL;
        }

        // 规则 3: 长历史 → 中模型 (更稳)
        if (historySize > 10) {
            log.debug("[model-route] 长历史 → MEDIUM");
            return MEDIUM;
        }

        // 默认
        return MEDIUM;
    }

    /** 是否支持 function call (小模型可能不支持) */
    public boolean supportsFunctionCall(String model) {
        return switch (model) {
            case SMALL -> true;  // qwen-turbo 新版支持
            default -> true;
        };
    }
}