package com.lenovo.ai.agent.core.impl;

import com.lenovo.ai.agent.core.AbstractAgent;
import com.lenovo.ai.agent.loop.AgentLoop;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 任务规划器。只跑一轮 LLM 产出 JSON 任务列表，不调工具，不对前端可见。
 * 注意：不要覆盖 execute()，用 AbstractAgent 默认实现（内部会走 AgentLoop）。
 */
@Component
public class PlannerAgent extends AbstractAgent {

    public PlannerAgent(AgentLoop loop) { super(loop); }

    @Override public String name() { return "planner"; }

    @Override public String rolePrompt() {
        return """
            你是任务规划器。把用户问题拆成若干子任务。
            每个子任务必须指定 agentName（knowledge / ticket / general）和 instruction。
            只输出 JSON 数组，不要任何解释、不要 markdown 代码块：
            [{"agentName":"knowledge","instruction":"..."}]
            如果问题简单，输出只含一个元素的数组。
            """;
    }

    @Override public List<String> allowedToolNames() { return List.of(); }
    @Override public List<String> intents() { return List.of("plan"); }
    @Override public int maxSteps() { return 1; }
    @Override public boolean streamToUser() { return false; }
}