package com.lenovo.ai.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lenovo.ai.agent.blackboard.AgentResult;
import com.lenovo.ai.agent.blackboard.AgentTask;
import com.lenovo.ai.agent.blackboard.Blackboard;
import com.lenovo.ai.agent.core.Agent;
import com.lenovo.ai.agent.core.AgentRegistry;
import com.lenovo.ai.agent.loop.AgentContext;
import com.lenovo.ai.infra.history.ChatHistoryStore;
import com.lenovo.ai.infra.sse.AiChatSseEmitter;
import com.lenovo.ai.llm.Message;
import com.lenovo.ai.memory.util.TextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 多 Agent 编排：Planner → 循环 Executor → Synthesizer。
 * 只有 Router 选中 planner 时才走这里。
 *
 * <p>SSE 约定：
 * <ul>
 *   <li>Planner / Synthesizer 静默（由 Agent.streamToUser() = false 保证）</li>
 *   <li>子任务 worker 通过 <b>子视图 emitter</b> 正常推送 delta，用户可见"正在查知识库…"</li>
 *   <li>子视图会吞掉 done / complete，<b>最终 done + complete 由本类统一发出</b></li>
 * </ul>
 *
 * <p>落库约定：
 * <ul>
 *   <li>内部 Agent（Planner / Synthesizer）streamToUser=false，不落库</li>
 *   <li>子任务 ctx 使用哨兵 messageId（{@link AgentContext#NO_PERSIST_ID}），
 *       <b>不落库</b>，避免中间结果污染主消息</li>
 *   <li>本类末尾显式把 finalAnswer 落到主 ctx 的 assistantMessageId，
 *       确保 fallback 场景下也有落库</li>
 * </ul>
 */
@Slf4j
@Component
public class Orchestrator {

    private static final int MAX_SUBTASKS = 5;

    /** 子任务整体时间预算（毫秒）：超出后剩余任务标记为超时，直接进汇总 */
    private static final long SUBTASK_BUDGET_MS = 120_000L;

    private final ObjectMapper objectMapper;
    private final AgentRegistry registry;
    private final ChatHistoryStore history;

    public Orchestrator(ObjectMapper objectMapper,
                        AgentRegistry registry,
                        ChatHistoryStore history) {
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.history = history;
    }

    public void execute(AgentContext ctx) {
        Blackboard board = ctx.blackboard();

        // =========================================================
        // 1. Planner 产出任务列表（静默）
        // =========================================================
        List<AgentTask> tasks = plan(ctx);
        if (tasks.isEmpty()) {
            log.warn(">>> 规划为空，降级到 general, runId={}", ctx.runId());
            degradeToGeneral(ctx);
            return;
        }
        for (AgentTask t : tasks) board.postTask(t);

        // =========================================================
// 2. 逐个执行子任务（子任务可见 delta，但不发终结帧）
// =========================================================
        long startedAt = System.currentTimeMillis();
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

            // ★ 整体预算：超预算后剩余任务不再执行，直接标记超时
            if (System.currentTimeMillis() - startedAt > SUBTASK_BUDGET_MS) {
                log.warn(">>> 子任务整体预算耗尽({}ms)，跳过剩余任务: runId={}, taskId={}",
                        SUBTASK_BUDGET_MS, ctx.runId(), task.taskId());
                AgentResult r = new AgentResult(task.taskId(), worker.name(), false,
                        "执行超时（整体预算耗尽）", Map.of());
                board.markTaskDone(task.taskId(), r);
                results.add(r);
                continue;
            }

            try {
                log.info(">>> 子任务执行: agent={}, taskId={}, instruction={}",
                        worker.name(), task.taskId(), preview(task.instruction()));

                // ★ P0-1 修复：子任务必须对前端可见。
                //   父 ctx 的 streamToUser 被 AiAgentRunner 置为 planner 的 false，
                //   fork 会继承这个 false，导致 worker 的 AbstractAgent 算出
                //   stream = worker.streamToUser() && sub.streamToUser() = false，
                //   子任务的 delta 全被静默，用户在多 Agent 模式下看不到任何中间过程。
                //   这里显式恢复可见性。
                AgentContext sub = ctx.fork(task.instruction(), true);
                sub.setStreamToUser(true);
                AiChatSseEmitter childView = ctx.emitter().childView();
                childView.unmute();                       // ★ 子视图本身也要 unmute
                childView.setSuppressComplete(true);      // ★ 子任务不发终结帧
                sub.setEmitter(childView);

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

        // =========================================================
        // 3. Synthesizer 汇总（静默）
        // =========================================================
        String summaryPrompt = buildSummaryPrompt(results);
        Agent synthesizer = registry.get("synthesizer");
        String finalAnswer = "";
        if (synthesizer != null) {
            AgentContext finalCtx = ctx.fork(summaryPrompt);
            runMuted(synthesizer, finalCtx);
            finalAnswer = finalCtx.lastAssistantContent();
        } else {
            log.warn(">>> synthesizer 未注册，直接使用降级答案, runId={}", ctx.runId());
        }

        if (finalAnswer == null || finalAnswer.isBlank()) {
            finalAnswer = fallbackAnswer(results);
            log.warn(">>> Synthesizer 输出为空，使用降级答案, runId={}", ctx.runId());
        }

        // =========================================================
        // 4. 写回主 ctx + 落库 + 推给用户
        // =========================================================
        ctx.messages().add(Message.assistant(finalAnswer));
        board.setFinalAnswer(finalAnswer);

        // ★ 关键：把最终答案落到主 ctx 的 assistantMessageId
        //   - 子任务已用哨兵 ID，不会污染这里
        //   - 保证 fallback 场景下也有落库
        //   - 保证 AiAgentRunner 的 loadAssistant 能读到真实答案
        try {
            history.appendAssistant(ctx.assistantMessageId(), ctx.sessionId(), finalAnswer);
        } catch (Exception e) {
            log.warn(">>> 落库最终答案失败: runId={}", ctx.runId(), e);
        }

        // ★ 推送最终答案给用户（终结帧统一由本类发出）
        try {
            ctx.emitter().send("delta", Map.of("text", finalAnswer));
        } catch (Exception e) {
            log.warn(">>> 推送最终答案失败: runId={}", ctx.runId(), e);
        }
        try {
            ctx.emitter().send("done", Map.of("messageId", ctx.assistantMessageId()));
        } catch (Exception e) {
            log.warn(">>> 推送 done 帧失败: runId={}", ctx.runId(), e);
        }
        ctx.emitter().complete();
    }

    /**
     * 降级到 general。
     *
     * <p><b>关键修复</b>：进入编排时 AiAgentRunner 已把 ctx.streamToUser 置为
     * planner 的 false。若直接复用该 ctx 执行 general，
     * {@code AbstractAgent} 里 {@code stream = 自身 && 父ctx} 的结果仍是 false，
     * 会被静默 —— 前端既收不到 delta，也收不到 done/complete，
     * 只能干等到 emitter 300s 超时。</p>
     *
     * <p>因此降级前必须先恢复可见性。</p>
     */
    private void degradeToGeneral(AgentContext ctx) {
        Agent general = registry.get("general");
        if (general == null) {
            log.error(">>> general 未注册，无法降级, runId={}", ctx.runId());
            sendTerminal(ctx, "抱歉，当前无法处理该问题，请稍后重试。");
            return;
        }

        ctx.setStreamToUser(true);
        ctx.emitter().unmute();
        ctx.emitter().setSuppressComplete(false);

        general.execute(ctx);   // general 自己会发 done + complete
    }

    /** 兜底：直接给前端一个终结响应并落库 */
    private void sendTerminal(AgentContext ctx, String text) {
        ctx.messages().add(Message.assistant(text));
        try {
            history.appendAssistant(ctx.assistantMessageId(), ctx.sessionId(), text);
        } catch (Exception e) {
            log.warn(">>> 落库兜底答案失败: runId={}", ctx.runId(), e);
        }
        try {
            ctx.emitter().send("delta", Map.of("text", text));
            ctx.emitter().send("done", Map.of("messageId", ctx.assistantMessageId()));
        } catch (Exception e) {
            log.warn(">>> 推送兜底答案失败: runId={}", ctx.runId(), e);
        }
        ctx.emitter().complete();
    }

    /**
     * 调 Planner 产出任务列表。
     * <p>Planner 是 streamToUser=false，AbstractAgent 会自动 mute + suppressComplete，
     * AgentLoop 也不会落库（哨兵 ID）。</p>
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
            log.warn(">>> Planner 输出为空, runId={}", ctx.runId());
            return List.of();
        }
        json = TextUtils.extractJsonArray(json);
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json,
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
            log.info(">>> Planner 产出 {} 个子任务, runId={}", tasks.size(), ctx.runId());
            return tasks;
        } catch (Exception e) {
            log.warn(">>> 规划 JSON 解析失败: runId={}, json={}",
                    ctx.runId(), preview(json), e);
            return List.of();
        }
    }

    /**
     * 内部 Agent 统一静默执行。
     * <p>虽然 AbstractAgent 已根据 streamToUser() 自动 mute，
     * 这里再包一层，防止子类忘记覆盖 streamToUser()。</p>
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

    private String preview(String s) {
        return Objects.isNull(s) ? "null"
                : (s.length() <= 200 ? s : s.substring(0, 200) + "...");
    }
}
