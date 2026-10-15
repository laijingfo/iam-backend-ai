package com.lenovo.ai.tools.impl;

import com.lenovo.ai.tools.AiTool;
import com.lenovo.ai.tools.ToolResult;
import com.lenovo.ai.tools.context.AiToolContext;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Component
public class DateTool implements AiTool {

    /**
     * 业务时区。
     *
     * <p>原实现用 {@code ZoneId.systemDefault()}。若服务器跑在 UTC，
     * 北京时间 00:00–08:00 这段窗口内 {@code LocalDate.now(UTC)} 会返回前一天，
     * 表现为"早上问今天几号，回答的是昨天"。</p>
     *
     * <p>业务在中国，默认锁定 Asia/Shanghai；如需调整走配置项。</p>
     */
    @Value("${ai.timezone:Asia/Shanghai}")
    private String defaultZoneId;

    @Override public String name() { return "date_tool"; }

    @Override
    public String description() {
        return "日期计算工具：计算两个日期之间的天数(daysBetween)，或获取当前日期(currentDate)。"
                + "仅当需要【日期计算】时才调用。"
                + "若用户只是问今天几号/今天星期几，system 消息的[当前环境信息]已给出准确日期，"
                + "直接采用即可，不要调用本工具，更禁止凭记忆回答。";
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
                // ★ 未显式指定时区时用业务时区，而非服务器默认时区
                ZoneId z = (zone == null || zone.isBlank())
                        ? businessZone() : ZoneId.of(zone);
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

    /** 业务时区；配置非法时回退 Asia/Shanghai，绝不用服务器默认时区 */
    private ZoneId businessZone() {
        if (defaultZoneId != null && !defaultZoneId.isBlank()) {
            try {
                return ZoneId.of(defaultZoneId.trim());
            } catch (Exception e) {
                log.warn("配置的业务时区非法: {}，回退 Asia/Shanghai", defaultZoneId);
            }
        }
        return ZoneId.of("Asia/Shanghai");
    }

    private String str(Object o) { return o == null ? null : String.valueOf(o); }
}