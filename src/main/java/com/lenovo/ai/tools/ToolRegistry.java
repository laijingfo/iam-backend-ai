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
     * 给 LLM 看的工具描述列表（仅按角色过滤）。
     * 保留此方法以兼容旧调用，内部委托给带 agentToolNames 的重载。
     */
    public List<ToolSpec> availableTools(AiToolContext ctx) {
        return availableTools(ctx, null);
    }

    /**
     * 给 LLM 看的工具描述列表（按角色 + Agent 白名单双重过滤）。
     *
     * @param ctx            工具上下文（含用户角色）
     * @param agentToolNames Agent 声明的工具白名单：
     *                       - null 或空列表：不限制（等价于全部）
     *                       - 含 "*"：全部工具
     *                       - 其他：仅列出的工具名
     * @return 过滤后的工具描述列表
     */
    public List<ToolSpec> availableTools(AiToolContext ctx, List<String> agentToolNames) {
        boolean allowAll = agentToolNames == null
                || agentToolNames.isEmpty()
                || agentToolNames.contains("*");
        return tools.values().stream()
                .filter(t -> isAllowed(t, ctx))
                .filter(t -> allowAll || agentToolNames.contains(t.name()))
                .map(t -> new ToolSpec(t.name(), t.description(), t.parameterSchema()))
                .toList();
    }

    /** 执行前二次校验，防止 LLM 幻觉调用未授权工具（仅按角色） */
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