package com.lenovo.controller.ai;

import com.lenovo.ai.history.ChatHistoryStore;
import com.lenovo.dto.AiChatPolicyResponse;
import com.lenovo.dto.AiChatSessionResponse;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.ai.AiAgentService;
import com.lenovo.util.I18nUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.lenovo.response.ApiResponse;

import java.util.List;
import java.util.Map;

@Tag(name = "AI 智能体管理", description = "基于大模型与知识库的智能问答接口")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiAgentController {

    private final AiAgentService aiAgentService;
    private final ChatHistoryStore history;

    @Operation(summary = "智能问答对话（SSE 流式）")
    @PostMapping(value = "/chat")
    public SseEmitter chat(@RequestBody ChatRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        return aiAgentService.chat(request, userId);
    }

    public record ChatRequest(String sessionId, String message, String knowledgeId) {}

    @GetMapping("/policy")
    public ApiResponse<AiChatPolicyResponse> policy() {

        return ApiResponse.success(history.policyStatus(SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/policy/accept")
    public ApiResponse<Void> acceptPolicy() {
        history.acceptPolicy(SecurityUtils.getCurrentUserId());
        return ApiResponse.successMessage(I18nUtil.get("ai.success.policy"));
    }
    @Operation(summary = "停止对话")
    @PostMapping("/chat/stop")
    public Map<String, Object> stopChat(@RequestBody StopRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        aiAgentService.stop(request.sessionId(), userId);
        return Map.of("success", true);
    }
    @GetMapping("/sessions")
    public ApiResponse<List<AiChatSessionResponse>> sessions() {
        return ApiResponse.success(history.sessions(SecurityUtils.getCurrentUserId()));
    }
    public record StopRequest(String sessionId) {}
}