package com.lenovo.ai.token.estimator;

/**
 * 启发式估算：中文 1.5 token/字，英文 1 token/4 字符，数字/标点按 1 token/3 字符。
 *
 * <p><b>装配方式</b>：本类不直接加 {@code @Component}，由
 * {@link TokenEstimatorConfig#heuristicTokenEstimator()} 显式注册。
 * 原因是 {@code @ConditionalOnMissingBean} 在 {@code @Component} 上
 * 评估时机不确定，可能导致 Bean 被跳过。</p>
 */
public class HeuristicTokenEstimator implements TokenEstimator {

    @Override
    public int estimate(String text) {
        if (text == null || text.isEmpty()) return 0;
        int chinese = 0, digit = 0, other = 0;
        for (char c : text.toCharArray()) {
            if (isChinese(c)) chinese++;
            else if (Character.isDigit(c)) digit++;
            else other++;
        }
        return (int) Math.ceil(chinese * 1.5 + digit / 2.5 + other / 4.0);
    }

    private static boolean isChinese(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF);
    }
}