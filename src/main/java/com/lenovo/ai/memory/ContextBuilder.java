package com.lenovo.ai.memory;

import com.lenovo.ai.infra.history.ChatHistoryStore;
import com.lenovo.ai.llm.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContextBuilder {

    private static final int KEEP_RECENT_TURNS = 6;
    private static final int MAX_CONTEXT_CHARS = 24000;

    private final ChatHistoryStore history;
    private final SummaryUtil summaryUtil;

    /**
     * 构建上下文。注意：newUserMsg 若为 null 则不追加，由调用方决定。
     * ✅ 修复：不再重复追加已在 history 中的消息。
     */
    public List<Message> build(String sessionId, String newUserMsg, String longTermMemoryBlock) {
        List<Message> stored = history.load(sessionId);
        List<Message> systemMsgs = stored.stream().filter(Message::isSystem).toList();
        List<Message> dialog = stored.stream().filter(m -> !m.isSystem()).toList();

        List<Message> result = new ArrayList<>(systemMsgs);

        if (longTermMemoryBlock != null && !longTermMemoryBlock.isBlank()) {
            result.add(Message.system("[关于用户的已知信息]\n" + longTermMemoryBlock));
        }

        if (dialog.size() <= KEEP_RECENT_TURNS * 2) {
            result.addAll(dialog);
        } else {
            int splitIdx = dialog.size() - KEEP_RECENT_TURNS * 2;
            List<Message> old = dialog.subList(0, splitIdx);
            List<Message> recent = dialog.subList(splitIdx, dialog.size());
            result.add(Message.system("[历史对话摘要]\n" + summaryUtil.summarize(old)));
            result.addAll(recent);
        }

        // ✅ 只在调用方明确要求时追加新消息
        if (newUserMsg != null && !newUserMsg.isBlank()) {
            result.add(Message.user(newUserMsg));
        }
        return trimToBudget(result);
    }

    private List<Message> trimToBudget(List<Message> messages) {
        int total = messages.stream().mapToInt(m -> len(m.content())).sum();
        if (total <= MAX_CONTEXT_CHARS) return messages;
        List<Message> result = new ArrayList<>(messages);
        int i = 0;
        while (total > MAX_CONTEXT_CHARS && i < result.size()) {
            Message m = result.get(i);
            if (m.isSystem()) { i++; continue; }
            total -= len(m.content());
            result.remove(i);
        }
        return result;
    }

    private int len(String s) { return s == null ? 0 : s.length(); }
}