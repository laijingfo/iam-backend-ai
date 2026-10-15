package com.lenovo.ai.memory.context;

import com.lenovo.ai.infra.history.ChatHistoryStore;
import com.lenovo.ai.llm.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContextBuilder {

    private static final int KEEP_RECENT_TURNS = 6;

    private final ChatHistoryStore history;
    private final SummaryService summaryService;

    /**
     * 业务时区。
     * <p>默认锁定 Asia/Shanghai，避免服务器跑在 UTC 时"早上问日期回答昨天"。</p>
     */
    @Value("${ai.timezone:Asia/Shanghai}")
    private String zoneId;

    private static final DateTimeFormatter CN_DATE =
            DateTimeFormatter.ofPattern("yyyy年M月d日");


    /** 兼容旧签名 */
    public List<Message> build(String sessionId, String newUserMsg, String longTermMemoryBlock) {
        return build(sessionId, newUserMsg, null, null, longTermMemoryBlock);
    }

    /** 兼容 4 参数 */
    public List<Message> build(String sessionId, String newUserMsg,
                               String userProfileBlock, String longTermMemoryBlock) {
        return build(sessionId, newUserMsg, userProfileBlock, null, longTermMemoryBlock);
    }

    /**
     * 完整签名：身份卡片 + 提及用户卡片 + 长期记忆块分开注入。
     */
    public List<Message> build(String sessionId, String newUserMsg,
                               String userProfileBlock,
                               String mentionedUserBlock,
                               String longTermMemoryBlock) {
        List<Message> stored = history.load(sessionId);
        List<Message> systemMsgs = stored.stream().filter(Message::isSystem).toList();
        List<Message> dialog = stored.stream().filter(m -> !m.isSystem()).toList();

        List<Message> result = new ArrayList<>(systemMsgs);

        // ★ 环境信息（当前日期）：确定性事实，每次实时生成
        //   放在身份卡片之前，作为最基础的事实锚点
        result.add(Message.system(buildEnvBlock()));

        // ★ 身份卡片：放最前面，优先级最高
        if (userProfileBlock != null && !userProfileBlock.isBlank()) {
            result.add(Message.system(userProfileBlock));
        }
        // ★ 提及用户
        if (mentionedUserBlock != null && !mentionedUserBlock.isBlank()) {
            result.add(Message.system(mentionedUserBlock));
        }
        // 长期记忆
        if (longTermMemoryBlock != null && !longTermMemoryBlock.isBlank()) {
            result.add(Message.system("[关于用户的已知信息]\n" + longTermMemoryBlock));
        }

        if (dialog.size() <= KEEP_RECENT_TURNS * 2) {
            result.addAll(dialog);
        } else {
            int splitIdx = dialog.size() - KEEP_RECENT_TURNS * 2;
            List<Message> old = dialog.subList(0, splitIdx);
            List<Message> recent = dialog.subList(splitIdx, dialog.size());

            // ★ P0-1 修复：把 sessionId 传给 summarize，隔离缓存 key
            String summary = summaryService.summarize(sessionId, old);
            if (summary != null && !summary.isBlank()) {
                result.add(Message.assistant("[历史对话摘要]\n" + summary));
            }
            result.addAll(recent);
        }

        if (newUserMsg != null && !newUserMsg.isBlank()) {
            // ★ 修复：调用方通常已在入库后再构建上下文，
            //   此时 history.load 的结果里已包含这条 user 消息，
            //   直接追加会导致同一条消息在上下文里出现两次。
            if (isAlreadyPresent(result, newUserMsg)) {
                log.debug(">>> 本轮用户消息已存在于历史中，跳过重复注入, sessionId={}", sessionId);
            } else {
                result.add(Message.user(newUserMsg));
            }
        }
        return result;
    }


    /**
     * 构建环境信息块：把"今天"作为确定性事实直接注入。
     *
     * <p><b>为什么不用工具</b>：问"今天几号"时依赖模型主动调 date_tool 不可靠——
     * 实测 GeneralAgent 会把这类问题归到"通用知识/闲聊"分支直接作答，
     * 于是凭训练数据编造一个日期。日期是确定性信息，
     * 直接注入 system 消息可以做到 100% 准确、零延迟、零 token 成本。</p>
     */
    private String buildEnvBlock() {
        ZoneId z;
        try {
            z = (zoneId == null || zoneId.isBlank())
                    ? ZoneId.of("Asia/Shanghai") : ZoneId.of(zoneId.trim());
        } catch (Exception e) {
            log.warn("配置的业务时区非法: {}，回退 Asia/Shanghai", zoneId);
            z = ZoneId.of("Asia/Shanghai");
        }

        LocalDate today = LocalDate.now(z);
        String week = toChineseWeek(today.getDayOfWeek());

        return "[当前环境信息]\n"
                + "- 当前日期: " + today.format(CN_DATE) + "（" + week + "）\n"
                + "- ISO 日期: " + today + "（日期计算请以这个为准）\n"
                + "- 时区: " + z.getId() + "\n"
                + "注意：这是系统的准确时间。用户问今天几号/星期几时直接采用，"
                + "不要凭自己的知识推测。\n";
    }

    private String toChineseWeek(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> "星期一";
            case TUESDAY -> "星期二";
            case WEDNESDAY -> "星期三";
            case THURSDAY -> "星期四";
            case FRIDAY -> "星期五";
            case SATURDAY -> "星期六";
            case SUNDAY -> "星期日";
        };
    }

    /**
     * 判断 newUserMsg 是否已经作为最后一条 user 消息存在。
     *
     * <p><b>★ 只看最后一条 user</b>：调用方（ChatService / AiAgentRunner）都是
     * "先 appendUser 再 buildContext"，append 同步可读时，刚写的那条必然在末尾。
     * 只看最后一条能避免"最后 4 条里有历史相同内容"导致的误判——
     * 那种误判会让本轮消息被跳过，LLM 看到历史消息重复作答。</p>
     *
     * <p>若某条路径没先落库（append 失败或异步写），最后一条 user 是历史消息，
     * 内容通常与 newUserMsg 不同 → 不跳过 → 注入本轮。宁可多注入一次也不漏。</p>
     */
    private boolean isAlreadyPresent(List<Message> messages, String newUserMsg) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m.role() != com.lenovo.ai.llm.enums.MessageRole.USER) continue;
            // 找到最后一条 user 就返回，不再往前扫
            return newUserMsg.equals(m.content());
        }
        return false;
    }
}