package com.lenovo.ai.token;


import org.springframework.stereotype.Component;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-12 17:19
 * @project iam-backend-ai
 * @description
 * Token 估算 (不需要调用 API)
 * 中文: 1 字 ≈ 1.5 token
 * 英文: 1 词 ≈ 1.3 token (按字符数 / 4 大致估算)
 */
@Component
public class TokenEstimator {

    public int estimate(String text) {
        if (text == null || text.isEmpty()) return 0;

        int chineseCount = 0;
        int otherCount = 0;
        for (char c : text.toCharArray()) {
            if (isChinese(c)) chineseCount++;
            else otherCount++;
        }
        // 中文 1 字 ≈ 1.5 token; 英文按 4 字符 ≈ 1 token
        return (int) Math.ceil(chineseCount * 1.5 + otherCount / 4.0);
    }

    private static boolean isChinese(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }
}