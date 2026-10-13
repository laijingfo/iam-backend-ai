package com.lenovo.ai.token.model;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-13
 * @project iam-backend-ai
 * @description
 * 模型价格（单位：¥ / 千 token）
 *
 * @param input  输入价格（¥ / 千 token）
 * @param output 输出价格（¥ / 千 token）
 */
public record ModelPrice(double input, double output) {

    /** 兜底默认价格（qwen-max 档位） */
    public static final ModelPrice DEFAULT = new ModelPrice(0.02, 0.06);
}