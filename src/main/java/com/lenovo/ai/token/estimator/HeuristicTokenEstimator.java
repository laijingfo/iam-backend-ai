package com.lenovo.ai.token.estimator;


/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-13 10:59
 * @project iam-backend-ai
 * @description
 * 启发式估算：中文 1.5 token/字，英文 1 token/4 字符，数字/标点按 1 token/3 字符
 */
//@Component
//@ConditionalOnMissingBean(TokenEstimator.class)
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
        // 数字串（如 ID、时间戳）token 密度高，按 1 token/2.5 字符
        return (int) Math.ceil(chinese * 1.5 + digit / 2.5 + other / 4.0);
    }

    private static boolean isChinese(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF);
    }
}
