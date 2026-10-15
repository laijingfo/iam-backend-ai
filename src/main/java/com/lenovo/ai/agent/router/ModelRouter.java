package com.lenovo.ai.agent.router;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 模型路由器：根据 Agent / 问题复杂度选择模型。
 *
 * <p><b>模型名一律走配置，禁止硬编码。</b></p>
 *
 * <p>历史教训：本类原先硬编码 {@code qwen-turbo / kimi-k2.6 / qwen-max}。
 * 在模型名真正透传给 LLM 之前，这些值只出现在日志里，实际永远使用
 * StreamingChatModel Bean 配置的模型 —— 硬编码错误被"路由不生效"掩盖。
 * 一旦修好透传，短消息场景立刻返回 {@code Model not exist}。</p>
 *
 * <p>因此现在的设计是：<b>未显式配置分级模型时，一律返回 Bean 默认模型</b>。
 * 即"不配置分级"等价于"全部用已验证可用的模型"，保证任何环境下都不会
 * 因为模型名不存在而失败。分级是<b>优化项</b>，不是必选项。</p>
 *
 * <p>启用分级前请先用 {@code GET /v1/models} 确认网关实际开放的模型名。</p>
 */
@Slf4j
@Component
public class ModelRouter {

    /** @deprecated 仅作兼容保留，请勿再引用；实际生效值以配置为准 */
    @Deprecated public static final String SMALL  = "qwen-turbo";
    /** @deprecated 仅作兼容保留，请勿再引用；实际生效值以配置为准 */
    @Deprecated public static final String MEDIUM = "kimi-k2.6";
    /** @deprecated 仅作兼容保留，请勿再引用；实际生效值以配置为准 */
    @Deprecated public static final String LARGE  = "qwen-max";

    /** 分级模型：留空表示"不启用分级"，回退 Bean 默认模型 */
    @Value("${ai.model.small:}")
    private String smallModel;

    @Value("${ai.model.medium:}")
    private String mediumModel;

    @Value("${ai.model.large:}")
    private String largeModel;

    /**
     * Bean 默认模型名。
     * 这是唯一"已验证可用"的模型——服务能启动就说明它是通的，
     * 因此作为所有未配置场景的兜底。
     */
    @Value("${langchain4j.open-ai.streaming-chat-model.model-name:}")
    private String beanModelName;

    /** 兜底的兜底：连 Bean 配置都没读到时（理论上不会） */
    private static final String LAST_RESORT = "qwen3-max";

    /** 当前实际生效的默认模型 */
    public String defaultModel() {
        if (beanModelName != null && !beanModelName.isBlank()) {
            return beanModelName.trim();
        }
        return LAST_RESORT;
    }

    /**
     * 选择模型。
     *
     * @param agentName   Agent 名 (planner / knowledge / ...)
     * @param message     用户消息
     * @param historySize 历史消息数
     */
    public String select(String agentName, String message, int historySize) {
        // 规则 1: planner 复杂推理 → 大模型
        if ("planner".equalsIgnoreCase(agentName)) {
            return pick(largeModel, "planner→LARGE");
        }

        // 规则 2: 极短消息 (打招呼、确认) → 小模型
        if (message != null && message.length() < 15 && historySize < 3) {
            return pick(smallModel, "短消息→SMALL");
        }

        // 规则 3: 长历史 → 中模型 (更稳)
        if (historySize > 10) {
            return pick(mediumModel, "长历史→MEDIUM");
        }

        // 默认
        return pick(mediumModel, "默认→MEDIUM");
    }

    /**
     * 取分级模型；未配置时回退 Bean 默认模型。
     *
     * <p>这是整个类最关键的保险丝：它保证"没配分级"时行为与修复前完全一致，
     * 不会因为硬编码模型名不存在而导致请求失败。</p>
     */
    private String pick(String configured, String rule) {
        String model = (configured == null || configured.isBlank())
                ? defaultModel()
                : configured.trim();
        log.debug("[model-route] {}, model={}", rule, model);
        return model;
    }

    /**
     * 是否支持 function call。
     *
     * <p>原实现所有分支都返回 true，等于未实现。
     * 这里改为：未知模型一律假定支持（保持既有行为，避免误伤），
     * 但显式配置了不支持列表的模型会被排除。</p>
     *
     * <p>若发现某模型不支持工具调用，加入配置
     * {@code ai.model.no-function-call} 即可。</p>
     */
    @Value("${ai.model.no-function-call:}")
    private String noFunctionCallModels;

    public boolean supportsFunctionCall(String model) {
        if (model == null || model.isBlank()) return true;
        if (noFunctionCallModels == null || noFunctionCallModels.isBlank()) return true;
        for (String m : noFunctionCallModels.split(",")) {
            if (m.trim().equalsIgnoreCase(model.trim())) {
                return false;
            }
        }
        return true;
    }
}
