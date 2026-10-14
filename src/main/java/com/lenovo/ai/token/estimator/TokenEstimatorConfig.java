package com.lenovo.ai.token.estimator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-13
 * @project iam-backend-ai
 * @description
 * TokenEstimator 装配：
 *   - 默认注册 HeuristicTokenEstimator（启发式估算，零依赖、零开销）
 *   - 若容器中已有其他 TokenEstimator（如 TiktokenEstimator），则不注册默认实现
 *   - 可通过 token.estimator.type=heuristic|tiktoken 显式选择
 *
 * 为什么用 @Configuration + @Bean 而不是 @Component + @ConditionalOnMissingBean：
 *   @ConditionalOnMissingBean 只在 @Configuration 的 @Bean 方法上可靠生效；
 *   放在 @Component 类上时，Spring 扫描阶段评估时机不确定，
 *   常导致条件不成立、Bean 被跳过（本次启动失败即此原因）。
 */
@Slf4j
@Configuration
public class TokenEstimatorConfig {

    /**
     * 默认：启发式估算。
     * 条件：token.estimator.type 未配置 或 = heuristic
     */
    @Bean
    @ConditionalOnProperty(
            name = "token.estimator.type",
            havingValue = "heuristic",
            matchIfMissing = true)
    public TokenEstimator heuristicTokenEstimator() {
        log.info("[token-estimator] 注册启发式估算器 HeuristicTokenEstimator");
        return new HeuristicTokenEstimator();
    }

    /**
     * 可选：精确估算（jtokkit）。
     * 条件：token.estimator.type = tiktoken
     */
    @Bean
    @ConditionalOnProperty(name = "token.estimator.type", havingValue = "tiktoken")
    public TokenEstimator tiktokenEstimator() {
        log.info("[token-estimator] 注册精确估算器 TiktokenEstimator");
        return new TiktokenEstimator();
    }
}