package com.lenovo.ai.agent;

import com.lenovo.ai.agent.blackboard.Blackboard;
import com.lenovo.ai.agent.blackboard.BlackboardFactory;
import com.lenovo.ai.agent.core.Agent;
import com.lenovo.ai.agent.loop.AgentContext;
import com.lenovo.ai.agent.router.AgentRouter;
import com.lenovo.ai.agent.router.ModelRouter;
import com.lenovo.ai.agent.router.RoutingRequest;
import com.lenovo.ai.infra.history.ChatHistoryStore;
import com.lenovo.ai.infra.lock.RedisLockHelper;
import com.lenovo.ai.infra.sse.AiChatSseEmitter;
import com.lenovo.ai.llm.Message;
import com.lenovo.ai.llm.enums.MessageRole;
import com.lenovo.ai.memory.context.ContextBuilder;
import com.lenovo.ai.memory.extractor.MemoryExtractor;
import com.lenovo.ai.memory.model.MemoryItem;
import com.lenovo.ai.memory.store.UserMemoryStore;
import com.lenovo.ai.token.context.ContextTrimmer;
import com.lenovo.ai.tools.context.AiToolContext;
import com.lenovo.entity.LenovoUser;
import com.lenovo.service.LenovoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiAgentRunner {

    private static final String MULTI_AGENT = "planner";

    /**
     * itCode 识别正则。
     * <p>匹配：4~20 位、字母开头、字母+数字。</p>
     * <p>示例：lidd8、wangel2、laijf2、zhangsan01。</p>
     */
    private static final Pattern ITCODE_PATTERN =
            Pattern.compile("\\b([a-zA-Z][a-zA-Z0-9]{3,19})\\b");

    /** 一次最多查询的提及用户数，避免远程调用打爆 */
    private static final int MAX_MENTIONED_USERS = 3;

    private final ContextBuilder contextBuilder;
    private final UserMemoryStore userMemoryStore;
    private final MemoryExtractor memoryExtractor;
    private final ChatHistoryStore history;
    private final RedisLockHelper redisLockHelper;

    private final AgentRouter agentRouter;
    private final BlackboardFactory blackboardFactory;
    private final Orchestrator orchestrator;

    private final ContextTrimmer contextTrimmer;
    private final ModelRouter modelRouter;

    /** ★ 联想用户服务 */
    private final LenovoService lenovoService;

    public void run(ChatRequest request, AiToolContext toolContext, String userId,
                    boolean newSession, String sessionId,
                    long userMessageId, long assistantMessageId,
                    Locale locale, String memoryId, String lockKey,
                    String token, AiChatSseEmitter emitter,
                    String runId, String requestId) {

        boolean delegateCompleted = false;
        try {
            log.info(">>> 本轮用户输入原文: [{}], userId={}, sessionId={}, runId={}, requestId={}",
                    request.message(), userId, sessionId, runId, requestId);

            safeSend(emitter, "meta", Map.of(
                    "runId", runId,
                    "requestId", requestId,
                    "sessionId", sessionId));

            history.appendUser(userMessageId, sessionId, userId, request.message());

            // ★ 1. 当前登录用户身份卡片（"我是谁"）
            String profileBlock = buildUserProfileBlock(userId, toolContext);

            // ★ 2. 输入里提到的其他用户
            String mentionedBlock = buildMentionedUserBlock(request.message(), userId);

            // ★ 3. 长期记忆
            List<MemoryItem> memories = userMemoryStore.recall(userId, request.message(), 5);
            String memoryBlock = memories.stream()
                    .map(m -> "- [" + m.type() + "] " + m.content())
                    .collect(Collectors.joining("\n"));

            // ★ 4. 分开注入
            List<Message> rawMessages = contextBuilder.build(
                    sessionId, request.message(), profileBlock, mentionedBlock, memoryBlock);
            List<Message> messages = trimContext(rawMessages);

            Agent agent = agentRouter.route(new RoutingRequest(
                    request.message(), userId, sessionId,
                    request.agentName(), toolContext.roleSet()));

            String modelName = modelRouter.select(
                    agent.name(), request.message(), Math.max(0, messages.size() - 1));

            AgentContext ctx = new AgentContext(
                    messages, toolContext, emitter, sessionId,
                    assistantMessageId, runId, memoryId);
            ctx.setAgentName(agent.name());
            ctx.setAllowedToolNames(agent.allowedToolNames());
            ctx.setMaxSteps(agent.maxSteps());
            ctx.setStreamToUser(agent.streamToUser());
            ctx.setModelName(modelName);

            Blackboard board = blackboardFactory.forRun(runId);
            ctx.setBlackboard(board);

            String requested = request.agentName();
            boolean isMulti = MULTI_AGENT.equalsIgnoreCase(
                    requested == null ? "" : requested.trim());

            if (isMulti || MULTI_AGENT.equals(agent.name())) {
                orchestrator.execute(ctx);
            } else {
                agent.execute(ctx);
            }
            delegateCompleted = true;

            String assistantContent = history.loadAssistant(assistantMessageId);
            if (assistantContent == null || assistantContent.isBlank()) {
                log.warn(">>> 助手消息为空，跳过记忆抽取: runId={}", runId);
            } else {
                List<Message> turn = List.of(
                        Message.user(request.message()),
                        Message.assistant(assistantContent)
                );
                memoryExtractor.extract(userId, turn);
            }

        } catch (Exception e) {
            log.error("AiAgentRunner 异常，runId={}", runId, e);
            safeSend(emitter, "error", Map.of(
                    "code", "INTERNAL_ERROR",
                    "message", e.getMessage() == null ? "处理失败" : e.getMessage()));
        } finally {
            if (!delegateCompleted) {
                try {
                    emitter.unmute();
                    emitter.setSuppressComplete(false);
                    emitter.complete();
                } catch (Exception ignored) {}
            }
            try {
                redisLockHelper.unlock(lockKey, token);
            } catch (Exception e) {
                log.warn("解锁失败: lockKey={}", lockKey, e);
            }
        }
    }

    // =====================================================================
    //  ① 当前登录用户身份卡片
    // =====================================================================

    /**
     * 构建当前登录用户身份卡片。
     * <p>用 userId（itCode）调 lenovoService.getUserInfoByItCode 拿完整信息。</p>
     */
    private String buildUserProfileBlock(String userId, AiToolContext toolContext) {
        if (userId == null || userId.isBlank()) return "";

        StringBuilder sb = new StringBuilder("[当前用户身份]\n");
        sb.append("- itCode: ").append(userId).append("\n");

        // ★ 调 Lenovo 接口拿完整信息
        try {
            LenovoUser user = lenovoService.getUserInfoByItCode(userId);
            if (user != null) {
                appendIfPresent(sb, "姓名", user.getRealName());
                appendIfPresent(sb, "部门", user.getLenovoDept());
                appendIfPresent(sb, "职位", user.getBusinessTitle());
                appendIfPresent(sb, "职级", user.getManagementLevel());
                appendIfPresent(sb, "业务单元", user.getBu());
                appendIfPresent(sb, "邮箱", user.getEmail());
                appendIfPresent(sb, "上级", user.getManager1st());
                if ("1".equals(user.getBandFlag())) {
                    sb.append("- 是否高管: 是\n");
                }
            } else {
                log.warn(">>> lenovoService 未查到用户: itCode={}", userId);
            }
        } catch (Exception e) {
            log.warn(">>> 查询当前用户信息失败: itCode={}", userId, e);
        }

        // 角色从 AiToolContext 取
        if (toolContext != null) {
            Set<String> roles = toolContext.roleSet();
            if (roles != null && !roles.isEmpty()) {
                sb.append("- 角色: ").append(String.join(", ", roles)).append("\n");
            }
        }

        sb.append("\n【关于\"我\"的说明】\n");
        sb.append("1. 当用户说\"我\"、\"我的\"、\"我是\"时，指的就是上面这个用户，");
        sb.append("不要把\"我\"当成匿名用户或未登录用户。\n");
        sb.append("2. 用户问\"我是谁\"、\"我的职位是什么\"、\"我是经理吗\"这类问题时，");
        sb.append("直接根据上面的身份信息回答，不要反问\"您是谁\"。\n");
        sb.append("3. 如果上面的身份信息不完整（比如没有职位），");
        sb.append("如实告知\"系统中未记录您的职位信息\"，不要编造。\n");
        sb.append("4. 调用工具时，涉及\"我的 xxx\"的场景，");
        sb.append("使用上面这个 itCode，不要向用户索要 itCode。\n");

        return sb.toString();
    }

    // =====================================================================
    //  ② 输入里提到的其他用户（lidd8、wangel2 等）
    // =====================================================================

    /**
     * 扫描用户输入，识别 itCode，调 Lenovo 接口拿信息，拼成"提及用户"卡片。
     * <p>排除当前登录用户自己；最多查 MAX_MENTIONED_USERS 个。</p>
     */
    private String buildMentionedUserBlock(String message, String currentUserId) {
        if (message == null || message.isBlank()) return "";

        // 1. 识别候选 itCode
        Set<String> candidates = extractItCodes(message);
        if (candidates.isEmpty()) return "";

        // 2. 排除当前登录用户自己
        if (currentUserId != null) {
            candidates.remove(currentUserId);
        }
        if (candidates.isEmpty()) return "";

        // 3. 逐个查 Lenovo 接口
        StringBuilder sb = new StringBuilder("[用户提到的其他用户]\n");
        int hit = 0;
        for (String itCode : candidates) {
            if (hit >= MAX_MENTIONED_USERS) break;
            try {
                LenovoUser user = lenovoService.getUserInfoByItCode(itCode);
                if (user == null) {
                    log.debug(">>> 提及的 itCode 未查到用户: {}", itCode);
                    continue;
                }
                sb.append("- itCode: ").append(itCode);
                appendInline(sb, "姓名", user.getRealName());
                appendInline(sb, "部门", user.getLenovoDept());
                appendInline(sb, "职位", user.getBusinessTitle());
                appendInline(sb, "职级", user.getManagementLevel());
                appendInline(sb, "邮箱", user.getEmail());
                sb.append("\n");
                hit++;
            } catch (Exception e) {
                log.warn(">>> 查询提及用户信息失败: itCode={}", itCode, e);
            }
        }
        if (hit == 0) return "";

        sb.append("\n【说明】以上是用户在问题中提到的其他用户，");
        sb.append("回答时可以用这些信息，但不要把它们当成当前登录用户。\n");
        return sb.toString();
    }

    /**
     * 从文本里提取候选 itCode。
     * <p>规则：4~20 位、字母开头、字母+数字组合；会过滤常见英文单词。</p>
     */
    private Set<String> extractItCodes(String text) {
        Set<String> result = new LinkedHashSet<>();
        if (text == null || text.isBlank()) return result;

        Matcher m = ITCODE_PATTERN.matcher(text);
        while (m.find()) {
            String token = m.group(1);
            // 过滤：必须包含数字，或长度 >= 5 且全字母
            boolean hasDigit = token.chars().anyMatch(Character::isDigit);
            boolean allLetter = token.chars().allMatch(Character::isLetter);
            if (!hasDigit && !(allLetter && token.length() >= 5)) {
                continue;
            }
            if (isCommonWord(token)) continue;
            result.add(token);
        }
        return result;
    }

    private boolean isCommonWord(String s) {
        return switch (s.toLowerCase()) {
            case "hello", "world", "test", "user", "name", "code",
                 "the", "and", "for", "with", "from", "this", "that",
                 "what", "where", "which", "who", "when", "why", "how" -> true;
            default -> false;
        };
    }

    // =====================================================================
    //  工具方法
    // =====================================================================

    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("- ").append(label).append(": ").append(value).append("\n");
        }
    }

    /** 单行内联追加，用于"提及用户"卡片 */
    private void appendInline(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("，").append(label).append(": ").append(value);
        }
    }

    private void safeSend(AiChatSseEmitter emitter, String event, Object data) {
        try {
            emitter.send(event, data);
        } catch (Exception e) {
            log.warn("发送 {} 帧失败", event, e);
        }
    }

    private List<Message> trimContext(List<Message> rawMessages) {
        if (rawMessages == null || rawMessages.isEmpty()) {
            return rawMessages;
        }
        try {
            List<Message> systems = rawMessages.stream()
                    .filter(m -> m.role() == MessageRole.SYSTEM)
                    .toList();
            List<Message> others = rawMessages.stream()
                    .filter(m -> m.role() != MessageRole.SYSTEM)
                    .collect(Collectors.toList());

            Message current = null;
            for (int i = others.size() - 1; i >= 0; i--) {
                if (others.get(i).role() == MessageRole.USER) {
                    current = others.remove(i);
                    break;
                }
            }
            if (current == null) {
                return rawMessages;
            }
            return contextTrimmer.trim(systems, others, current, 0);
        } catch (Exception e) {
            log.warn("[context-trim] 裁剪失败，使用原始上下文", e);
            return rawMessages;
        }
    }
}