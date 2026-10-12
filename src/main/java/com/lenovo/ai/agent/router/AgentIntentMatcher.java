package com.lenovo.ai.agent.router;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 轻量意图识别：先用关键词，命中不了返回 unknown。
 * 后续可替换为向量或小模型，只要实现同一接口。
 */
@Slf4j
@Component
public class AgentIntentMatcher {

    private static final Map<String, String> KEYWORD_TO_INTENT = Map.ofEntries(
            Map.entry("工单", "create_ticket"),
            Map.entry("报障", "report_issue"),
            Map.entry("反馈", "feedback"),
            Map.entry("故障", "report_issue"),
            Map.entry("审批", "approval"),
            Map.entry("审核", "approval"),
            Map.entry("制度", "policy"),
            Map.entry("流程", "policy"),
            Map.entry("报销", "policy"),
            Map.entry("文档", "document"),
            Map.entry("知识库", "knowledge"),
            Map.entry("待办", "business_query"),
            Map.entry("你好", "chitchat"),
            Map.entry("谢谢", "chitchat")
    );

    public String match(String question) {
        if (question == null || question.isBlank()) return "unknown";
        String q = question.toLowerCase();
        for (Map.Entry<String, String> e : KEYWORD_TO_INTENT.entrySet()) {
            if (q.contains(e.getKey())) {
                log.debug(">>> 意图匹配命中: keyword={}, intent={}", e.getKey(), e.getValue());
                return e.getValue();
            }
        }
        return "unknown";
    }
}