package com.lenovo.ai.tools;

import com.lenovo.ai.tools.context.AiToolContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, AiTool> tools = new ConcurrentHashMap<>();

    public ToolRegistry(List<AiTool> allTools) {
        for (AiTool t : allTools) {
            AiTool prev = tools.put(t.name(), t);
            if (prev != null) log.warn("工具名冲突: {}", t.name());
        }
        log.info(">>> 工具注册完成，共 {} 个: {}", tools.size(), tools.keySet());
    }

    public AiTool get(String name) { return tools.get(name); }

    /**
     * 工具列表过滤。
     * <p><b>语义约定（重要）</b>：</p>
     * <ul>
     *   <li>{@code null}     → 全部工具（兼容旧调用）</li>
     *   <li>{@code ["*"]}    → 全部工具</li>
     *   <li>{@code []}（空） → <b>不返回任何工具</b>（Planner/Synthesizer 场景）</li>
     *   <li>其他              → 白名单</li>
     * </ul>
     */
    public List<ToolSpec> availableTools(AiToolContext ctx, List<String> agentToolNames) {
        // ★ 空列表 = 无工具（修复语义）
        if (agentToolNames != null && agentToolNames.isEmpty()) {
            return List.of();
        }
        boolean allowAll = agentToolNames == null || agentToolNames.contains("*");
        return tools.values().stream()
                .filter(t -> isAllowed(t, ctx))
                .filter(t -> allowAll || agentToolNames.contains(t.name()))
                .map(t -> new ToolSpec(t.name(), t.description(), t.parameterSchema()))
                .toList();
    }

    /** 兼容旧调用 */
    public List<ToolSpec> availableTools(AiToolContext ctx) {
        return availableTools(ctx, null);
    }

    /**
     * 执行前双重校验：角色 + Agent 白名单。
     */
    public boolean isAllowed(String name, AiToolContext ctx, List<String> agentToolNames) {
        AiTool t = tools.get(name);
        if (t == null) return false;
        if (!isAllowed(t, ctx)) return false;
        // ★ Agent 白名单校验
        if (agentToolNames == null || agentToolNames.contains("*")) return true;
        if (agentToolNames.isEmpty()) return false;
        return agentToolNames.contains(name);
    }

    /** 兼容旧调用（仅角色校验，不推荐） */
    @Deprecated
    public boolean isAllowed(String name, AiToolContext ctx) {
        AiTool t = tools.get(name);
        return t != null && isAllowed(t, ctx);
    }

    private boolean isAllowed(AiTool tool, AiToolContext ctx) {
        if (tool.allowedRoles().contains("*")) return true;
        if (ctx == null) return false;
        return tool.allowedRoles().stream().anyMatch(ctx::hasRole);
    }
}