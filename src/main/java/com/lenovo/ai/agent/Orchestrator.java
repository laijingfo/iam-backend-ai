package com.lenovo.ai.agent.orchestrate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lenovo.ai.agent.blackboard.AgentResult;
import com.lenovo.ai.agent.blackboard.AgentTask;
import com.lenovo.ai.agent.blackboard.Blackboard;
import com.lenovo.ai.agent.core.Agent;
import com.lenovo.ai.agent.core.AgentRegistry;
import com.lenovo.ai.agent.loop.AgentContext;
import com.lenovo.ai.llm.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 多 Agent 编排：Planner → 循环 Executor → Synthesizer。
 * 只有 Router 选中 planner 时才走这里。
 *
 * SSE 约定：
 *  - Planner / Synthesizer 静默（由 Agent.streamToUser() = false 保证）
 *  - 子任务 worker 正常推送 delta，用户可见"正在查知识库…"
 *  - 最终 done + complete 由本类统一发出
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Orchestrator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_SUBTASKS = 5;

    private final AgentRegistry registry;

    public void execute(AgentContext ctx) {
        Blackboard board = ctx.blackboard();

        // 1. Planner 产出任务列表（静默）
        List<AgentTask> tasks = plan(ctx);
        if (tasks.isEmpty()) {
            log.warn(">>> 规划为空，降级到 general");
            registry.get("general").execute(ctx);
            // general 会自己发 done + complete
            return;
        }
        for (AgentTask t : tasks) board.postTask(t);

        // 2. 逐个执行子任务（子任务可见 delta）
        List<AgentResult> results = new ArrayList<>();
        for (AgentTask task : board.pendingTasks()) {
            Agent worker = registry.get(task.agentName());
            if (worker == null) {
                log.warn(">>> 未知 Agent: {}, taskId={}", task.agentName(), task.taskId());
                AgentResult r = new AgentResult(task.taskId(), "unknown", false,
                        "无此 Agent: " + task.agentName(), Map.of());
                board.markTaskDone(task.taskId(), r);
                results.add(r);
                continue;
            }
            try {
                log.info(">>> 子任务执行: agent={}, taskId={}, instruction={}",
                        worker.name(), task.taskId(), preview(task.instruction()));
                AgentContext sub = ctx.fork(task.instruction());
                worker.execute(sub);
                AgentResult r = new AgentResult(task.taskId(), worker.name(), true,
                        sub.lastAssistantContent(), Map.of());
                board.markTaskDone(task.taskId(), r);
                results.add(r);
            } catch (Exception e) {
                log.error(">>> 子任务执行失败: taskId={}", task.taskId(), e);
                AgentResult r = new AgentResult(task.taskId(), worker.name(), false,
                        e.getMessage(), Map.of());
                board.markTaskDone(task.taskId(), r);
                results.add(r);
            }
        }

        // 3. Synthesizer 汇总（静默）
        String summaryPrompt = buildSummaryPrompt(results);
        Agent synthesizer = registry.get("synthesizer");
        AgentContext finalCtx = ctx.fork(summaryPrompt);
        runMuted(synthesizer, finalCtx);
        String finalAnswer = finalCtx.lastAssistantContent();

        if (finalAnswer == null || finalAnswer.isBlank()) {
            finalAnswer = fallbackAnswer(results);
            log.warn(">>> Synthesizer 输出为空，使用降级答案");
        }

        // 4. 把汇总结果写回主 ctx，并通过主 emitter 推给用户
        ctx.messages().add(Message.assistant(finalAnswer));
        board.setFinalAnswer(finalAnswer);

        ctx.emitter().send("delta", Map.of("text", finalAnswer));
        ctx.emitter().send("done", Map.of("messageId", ctx.assistantMessageId()));
        ctx.emitter().complete();
    }

    /**
     * 调 Planner 产出任务列表。
     * Planner 是 streamToUser=false，AbstractAgent 会自动 mute + suppressComplete。
     */
    private List<AgentTask> plan(AgentContext ctx) {
        Agent planner = registry.get("planner");
        if (planner == null) return List.of();

        // 取最后一条 user 消息作为规划输入
        String lastUser = lastUserContent(ctx);
        AgentContext planCtx = ctx.fork(lastUser);
        runMuted(planner, planCtx);

        String json = planCtx.lastAssistantContent();
        if (json == null || json.isBlank()) {
            log.warn(">>> Planner 输出为空");
            return List.of();
        }
        json = stripCodeFence(json);
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {});
            List<AgentTask> tasks = new ArrayList<>();
            for (Map<String, Object> m : raw) {
                if (tasks.size() >= MAX_SUBTASKS) break;
                Object an = m.get("agentName");
                Object ins = m.get("instruction");
                if (an == null || ins == null) continue;
                String agentName = String.valueOf(an).trim();
                String instruction = String.valueOf(ins).trim();
                if (agentName.isEmpty() || instruction.isEmpty()) continue;
                tasks.add(new AgentTask(UUID.randomUUID().toString(),
                        agentName, instruction, Map.of()));
            }
            log.info(">>> Planner 产出 {} 个子任务", tasks.size());
            return tasks;
        } catch (Exception e) {
            log.warn(">>> 规划 JSON 解析失败: {}", preview(json), e);
            return List.of();
        }
    }

    /**
     * 内部 Agent 统一静默执行。
     * 虽然 AbstractAgent 已根据 streamToUser() 自动 mute，
     * 这里再包一层，防止子类忘记覆盖 streamToUser()。
     */
    private void runMuted(Agent agent, AgentContext ctx) {
        ctx.emitter().mute();
        ctx.emitter().setSuppressComplete(true);
        try {
            agent.execute(ctx);
        } finally {
            ctx.emitter().unmute();
            ctx.emitter().setSuppressComplete(false);
        }
    }

    private String buildSummaryPrompt(List<AgentResult> results) {
        StringBuilder sb = new StringBuilder("以下是各子任务结果：\n");
        for (AgentResult r : results) {
            sb.append("- [").append(r.agentName()).append("] ")
                    .append(r.success() ? "成功" : "失败")
                    .append("：").append(r.output() == null ? "" : r.output())
                    .append("\n");
        }
        sb.append("\n请整合成最终回答。");
        return sb.toString();
    }

    private String fallbackAnswer(List<AgentResult> results) {
        StringBuilder sb = new StringBuilder();
        for (AgentResult r : results) {
            if (r.success() && r.output() != null && !r.output().isBlank()) {
                sb.append(r.output()).append("\n");
            }
        }
        return sb.length() == 0 ? "抱歉，未能获取到有效结果。" : sb.toString();
    }

    private String lastUserContent(AgentContext ctx) {
        List<Message> msgs = ctx.messages();
        for (int i = msgs.size() - 1; i >= 0; i--) {
            Message m = msgs.get(i);
            if (m.role() == com.lenovo.ai.llm.enums.MessageRole.USER
                    && m.content() != null && !m.content().isBlank()) {
                return m.content();
            }
        }
        return "";
    }

    private String stripCodeFence(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```[a-zA-Z]*\\s*", "");
            int idx = t.lastIndexOf("```");
            if (idx >= 0) t = t.substring(0, idx);
            t = t.trim();
        }
        int start = t.indexOf('[');
        int end = t.lastIndexOf(']');
        if (start >= 0 && end > start) {
            t = t.substring(start, end + 1);
        }
        return t;
    }

    private String preview(String s) {
        if (s == null) return "null";
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}