package com.lenovo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.entity.SyncUarIgnoreRule;
import com.lenovo.mapper.UarIgnoreRuleMapper;
import com.lenovo.service.UarIgnoreRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class UarIgnoreRuleServiceImpl implements UarIgnoreRuleService {

    private final UarIgnoreRuleMapper uarIgnoreRuleMapper;

    @Override
    public Page<SyncUarIgnoreRule> getById(Integer page, Integer size, String cmdbId) {
        Page<SyncUarIgnoreRule> pageParam = new Page<>(page, size);
        return uarIgnoreRuleMapper.getById(pageParam, cmdbId);
    }

    @Override
    public int deleteRule(Long ruleId) {
        return uarIgnoreRuleMapper.deleteById(ruleId);
    }

    @Override
    public boolean isExistsRule(String cmdbId, String systemRole) {
        return uarIgnoreRuleMapper.isExistsRule(cmdbId, systemRole);
    }


    @Override
    public int addRule(String cmdbId, String systemRole) {
        return uarIgnoreRuleMapper.addRule(cmdbId, systemRole);
    }
}
