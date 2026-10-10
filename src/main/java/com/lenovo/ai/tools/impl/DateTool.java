package com.lenovo.ai.tools.impl;

import com.lenovo.ai.tools.AiTool;
import com.lenovo.ai.tools.ToolResult;
import com.lenovo.ai.tools.context.AiToolContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-09-30 16:19
 * @project iam-backend-ai
 * @description
 */
@Component
public class DateTool implements AiTool {

    @Override public String name() { return "date_tool"; }

    @Override
    public String description() {
        return "日期工具：获取当前日期(currentDate)，或计算两个日期之间的天数(daysBetween)。"
                + "当用户询问当前日期时必须调用，禁止凭记忆回答。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "action", Map.of(
                                "type", "string",
                                "enum", List.of("currentDate", "daysBetween"),
                                "description", "要执行的动作"),
                        "zoneId", Map.of("type", "string",
                                "description", "时区，如 Asia/Shanghai，可选"),
                        "startDate", Map.of("type", "string",
                                "description", "开始日期 YYYY-MM-DD，daysBetween 时必填"),
                        "endDate", Map.of("type", "string",
                                "description", "结束日期 YYYY-MM-DD，daysBetween 时必填")
                ),
                "required", List.of("action")
        );
    }

    @Override public Set<String> allowedRoles() { return Set.of("*"); }

    @Override
    public ToolResult execute(Map<String, Object> args, AiToolContext ctx) {
        String action = String.valueOf(args.get("action"));
        try {
            if ("currentDate".equals(action)) {
                String zone = str(args.get("zoneId"));
                ZoneId z = (zone == null || zone.isBlank())
                        ? ZoneId.systemDefault() : ZoneId.of(zone);
                return ToolResult.ok(LocalDate.now(z).toString());
            }
            if ("daysBetween".equals(action)) {
                LocalDate start = LocalDate.parse(str(args.get("startDate")));
                LocalDate end = LocalDate.parse(str(args.get("endDate")));
                return ToolResult.ok(String.valueOf(ChronoUnit.DAYS.between(start, end)));
            }
            return ToolResult.fail("未知 action: " + action);
        } catch (Exception e) {
            return ToolResult.fail("日期工具执行失败: " + e.getMessage());
        }
    }

    private String str(Object o) { return o == null ? null : String.valueOf(o); }
}