package com.lenovo.ai.memory.context;

import com.lenovo.ai.infra.history.ChatHistoryStore;
import com.lenovo.ai.llm.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContextBuilder {

    private static final int KEEP_RECENT_TURNS = 6;

    private final ChatHistoryStore history;
    private final SummaryService summaryService;

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
            String summary = summaryService.summarize(old);
            if (summary != null && !summary.isBlank()) {
                result.add(Message.assistant("[历史对话摘要]\n" + summary));
            }
            result.addAll(recent);
        }

        if (newUserMsg != null && !newUserMsg.isBlank()) {
            result.add(Message.user(newUserMsg));
        }
        return result;
    }
}