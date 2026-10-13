package com.lenovo.controller.ai;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-12 17:34
 * @project iam-backend-ai
 * @description
 */
import com.lenovo.ai.token.model.QuotaStatus;
import com.lenovo.ai.token.model.TokenUsageSummary;
import com.lenovo.ai.token.query.TokenQueryService;
import com.lenovo.ai.token.quota.TokenQuotaService;
import com.lenovo.ai.token.model.TokenUsageRecord;
import com.lenovo.response.ApiResponse;
import com.lenovo.security.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Token 管理", description = "配额 / 用量 / 成本查询")
@RestController
@RequestMapping("/ai/token")
@RequiredArgsConstructor
public class TokenController {

    private final TokenQuotaService quotaService;
    private final TokenQueryService queryService;

    /** 当前用户配额状态 */
    @Operation(summary = "查询当前用户配额状态")
    @GetMapping("/quota")
    public ApiResponse<QuotaStatus> quota() {
        String userId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success(quotaService.check(userId));
    }

    /** 当前用户今日用量 */
    @Operation(summary = "查询今日 token 用量明细")
    @GetMapping("/usage/today")
    public ApiResponse<TokenUsageSummary> usageToday() {
        String userId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success(queryService.todaySummary(userId));
    }

    /** 用户最近记录 */
    @Operation(summary = "查询最近 token 使用记录")
    @GetMapping("/records")
    public ApiResponse<List<TokenUsageRecord>> records(
            @RequestParam(defaultValue = "50") int limit) {
        String userId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success(queryService.recentRecords(userId, limit));
    }
}
