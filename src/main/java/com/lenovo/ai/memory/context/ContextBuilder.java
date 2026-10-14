package com.lenovo.ai.memory.context;

import com.lenovo.ai.infra.history.ChatHistoryStore;
import com.lenovo.ai.llm.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文组装器。
 *
 * @apiNote 仅限 memory 模块内部使用，外部调用方请通过
 *          {@link com.lenovo.ai.memory.facade.MemoryFacade#buildContext(String, String, String)}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextBuilder {

    private static final int KEEP_RECENT_TURNS = 6;

    private final ChatHistoryStore history;
    private final SummaryService summaryService;

    /**
     * 构建上下文。注意：newUserMsg 若为 null 则不追加，由调用方决定。
     * ✅ 裁剪统一由 ContextTrimmer 负责，本方法只负责组装。
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
            result.add(Message.system("[历史对话摘要]\n" + summaryService.summarize(old)));
            result.addAll(recent);
        }

        if (newUserMsg != null && !newUserMsg.isBlank()) {
            result.add(Message.user(newUserMsg));
        }
        return result;
    }
}