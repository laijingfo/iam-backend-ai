package com.lenovo.ai.tools.impl;

import com.lenovo.ai.tools.AiTool;
import com.lenovo.ai.tools.ToolResult;
import com.lenovo.ai.tools.context.AiToolContext;
import com.lenovo.entity.LenovoUser;
import com.lenovo.service.LenovoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按 itCode 查询联想员工信息。
 * <p>当用户问"laijf2 是谁"、"wang 的部门"、"laijf2 的上级"时调用。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryUserInfoTool implements AiTool {

    private final LenovoService lenovoService;

    @Override
    public String name() {
        return "query_user_info";
    }

    @Override
    public String description() {
        return "按 itCode 查询联想员工信息（姓名、部门、职位、职级、业务单元、邮箱、上级）。"
                + "当用户问某人是谁、某人的部门/职位/上级时调用。"
                + "itCode 形如 laijf2、lidd8（字母开头，字母+数字，长度 4~20）。"
                + "不要凭记忆回答，也不要回答\"我不认识\"。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "itCode", Map.of(
                                "type", "string",
                                "description", "员工的 itCode，如 laijf2、lidd8、wangel2")
                ),
                "required", List.of("itCode")
        );
    }

    /** 所有登录用户都可查人；如需限制，改成 Set.of("USER", "ADMIN") */
    @Override
    public Set<String> allowedRoles() {
        return Set.of("*");
    }

    @Override
    public ToolResult execute(Map<String, Object> args, AiToolContext ctx) {
        Object raw = args.get("itCode");
        if (raw == null) {
            return ToolResult.fail("itCode 不能为空");
        }

        // ★ 清洗参数：LLM 可能把"lidd8 是谁"整句传进来
        String itCode = String.valueOf(raw).trim()
                .replaceAll("[^a-zA-Z0-9]", "");
        if (itCode.isBlank()) {
            return ToolResult.fail("itCode 不能为空");
        }

        try {
            // ① 原样查
            LenovoUser user = lenovoService.getUserInfoByItCode(itCode);

            // ② 查不到再试小写（兼容大小写敏感的后端）
            if (user == null) {
                user = lenovoService.getUserInfoByItCode(itCode.toLowerCase());
            }

            if (user == null) {
                return ToolResult.fail("未查到用户: " + itCode);
            }

            String content = formatUser(itCode, user);
            return ToolResult.ok(content, Map.of("itCode", itCode));

        } catch (Exception e) {
            log.warn(">>> query_user_info 查询失败: itCode={}", itCode, e);
            return ToolResult.fail("查询失败: " + e.getMessage());
        }
    }

    /**
     * 把 LenovoUser 拼成自然语言。
     * <p>字段映射按 LenovoUser 真实字段：
     * userName / realName / lenovoDept / businessTitle /
     * managementLevel / bu / email / manager1st / bandFlag。</p>
     */
    private String formatUser(String itCode, LenovoUser user) {
        StringBuilder sb = new StringBuilder();
        sb.append("itCode: ").append(itCode);

        if (notBlank(user.getRealName())) {
            sb.append("，姓名: ").append(user.getRealName());
        }
        if (notBlank(user.getLenovoDept())) {
            sb.append("，部门: ").append(user.getLenovoDept());
        }
        if (notBlank(user.getBusinessTitle())) {
            sb.append("，职位: ").append(user.getBusinessTitle());
        }
        if (notBlank(user.getManagementLevel())) {
            sb.append("，职级: ").append(user.getManagementLevel());
        }
        if (notBlank(user.getBu())) {
            sb.append("，业务单元: ").append(user.getBu());
        }
        if (notBlank(user.getEmail())) {
            sb.append("，邮箱: ").append(user.getEmail());
        }
        if (notBlank(user.getManager1st())) {
            sb.append("，上级 itCode: ").append(user.getManager1st());
        }
        if ("1".equals(user.getBandFlag())) {
            sb.append("，是否高管: 是");
        }
        return sb.toString();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}