package com.lenovo.ai.agent.core.impl;

import com.lenovo.ai.agent.core.AbstractAgent;
import com.lenovo.ai.agent.loop.AgentLoop;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 结果汇总器。读子任务结果，整合成最终回答，不对前端可见。
 */
@Component
public class SynthesizerAgent extends AbstractAgent {

    public SynthesizerAgent(AgentLoop loop) { super(loop); }

    @Override public String name() { return "synthesizer"; }

    @Override public String rolePrompt() {
        return """
            你是结果汇总器。你会收到若干子任务的执行结果，
            请整合成一段连贯、准确的最终回答。
            不要编造未在结果中出现的信息。
            直接输出最终回答，不要解释你的工作过程。
            """;
    }

    @Override public List<String> allowedToolNames() { return List.of(); }
    @Override public List<String> intents() { return List.of("synthesize"); }
    @Override public int maxSteps() { return 1; }
    @Override public boolean streamToUser() { return false; }
}