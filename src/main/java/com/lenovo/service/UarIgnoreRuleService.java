package com.lenovo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.entity.SyncUarIgnoreRule;

public interface UarIgnoreRuleService {
    Page<SyncUarIgnoreRule> getById(Integer page, Integer size, String cmdbId);

    int deleteRule(Long ruleId);

    boolean isExistsRule(String cmdbId, String systemRole);

    int addRule(String cmdbId, String systemRole);
}
