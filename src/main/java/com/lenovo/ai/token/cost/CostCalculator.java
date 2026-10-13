package com.lenovo.ai.token.cost;

import com.lenovo.ai.token.model.TokenUsage;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-13 11:00
 * @project iam-backend-ai
 * @description
 */
public interface CostCalculator {
    double calculate(String modelName, TokenUsage usage);
}
