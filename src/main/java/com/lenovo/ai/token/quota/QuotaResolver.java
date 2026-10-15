package com.lenovo.ai.token.quota;

import com.lenovo.ai.token.config.TokenProperties;
import com.lenovo.ai.token.model.TokenQuota;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.lenovo.ai.token.model.TokenQuota.*;

@Component
@RequiredArgsConstructor
public class QuotaResolver {

    private final TokenProperties properties;

    public TokenQuota byTier(String userId, String tier) {
        if (TIER_DISABLED.equals(tier)) {
            return new TokenQuota(userId, tier, 0, 0, 0, false);
        }
        TokenProperties.TierConfig cfg = properties.getQuota().get(tier);
        if (cfg == null) cfg = properties.getQuota().get(properties.getDefaultTier());
        if (cfg == null) {
            return new TokenQuota(userId, TIER_FREE, 500_000L, 5_000_000L, 8_000, true);
        }
        long daily   = cfg.getDaily()   < 0 ? Long.MAX_VALUE : cfg.getDaily();
        long monthly = cfg.getMonthly() < 0 ? Long.MAX_VALUE : cfg.getMonthly();
        // ★ P1 修复：maxRequest <= 0 时视为不限，避免配置 -1 导致所有请求被拒
        int maxReq = cfg.getMaxRequest() <= 0 ? Integer.MAX_VALUE : cfg.getMaxRequest();
        return new TokenQuota(userId, tier, daily, monthly, maxReq, true);
    }
}