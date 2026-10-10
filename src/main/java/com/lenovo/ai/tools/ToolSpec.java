package com.lenovo.ai.tools;

import java.util.Map;
/**
 * @author laifo
 * @version 1.0
 * @date 2026-09-30 10:05
 * @project iam-backend-ai
 * @description
 */
public record ToolSpec(String name, String description, Map<String, Object> parameterSchema) {}
