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

    /** 给 LLM 看的工具描述列表（已按角色过滤） */
    public List<ToolSpec> availableTools(AiToolContext ctx) {
        return tools.values().stream()
                .filter(t -> isAllowed(t, ctx))
                .map(t -> new ToolSpec(t.name(), t.description(), t.parameterSchema()))
                .toList();
    }

    /** 执行前二次校验，防止 LLM 幻觉调用未授权工具 */
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