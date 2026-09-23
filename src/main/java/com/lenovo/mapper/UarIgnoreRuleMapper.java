package com.lenovo.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.entity.SyncUarIgnoreRule;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.stream.Collectors;

@Mapper
public interface UarIgnoreRuleMapper extends BaseMapper<SyncUarIgnoreRule> {
    default Page<SyncUarIgnoreRule> getById(
            Page<SyncUarIgnoreRule> pageParam,
            String cmdbId
    ) {
        return selectPage(
                pageParam,
                new LambdaQueryWrapper<SyncUarIgnoreRule>()
                        .eq(SyncUarIgnoreRule::getCmdbId, cmdbId)
                        .orderByDesc(SyncUarIgnoreRule::getRuleId)
        );
    }

    default boolean isExistsRule(String cmdbId, String systemRole) {
        return selectCount(
                new LambdaQueryWrapper<SyncUarIgnoreRule>()
                        .eq(SyncUarIgnoreRule::getCmdbId, cmdbId)
                        .eq(SyncUarIgnoreRule::getSystemRole, systemRole)
        ) > 0;
    }

    default int addRule(String cmdbId, String systemRole) {
        return insert(new SyncUarIgnoreRule(cmdbId, systemRole));
    }

    default List<String> getEntitiesByCmdbId(String cmdbId) {
        return selectList(
                new LambdaQueryWrapper<SyncUarIgnoreRule>()
                        .eq(SyncUarIgnoreRule::getCmdbId, cmdbId)
        ).stream().map(SyncUarIgnoreRule::getSystemRole).collect(Collectors.toList());
    }
}
