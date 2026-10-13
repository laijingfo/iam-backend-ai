package com.lenovo.ai.token.estimator;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-13 10:58
 * @project iam-backend-ai
 * @description
 */
public interface TokenEstimator {
    int estimate(String text);
    default int estimate(String... texts) {
        int sum = 0;
        for (String t : texts) sum += estimate(t);
        return sum;
    }
}
