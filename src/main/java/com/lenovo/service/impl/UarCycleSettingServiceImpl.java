package com.lenovo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.bean.UarCycleSettingBean;
import com.lenovo.entity.UarCycleSetting;
import com.lenovo.mapper.UarCycleSettingMapper;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.service.UarCycleSettingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UarCycleSettingServiceImpl extends ServiceImpl<UarCycleSettingMapper, UarCycleSetting> implements UarCycleSettingService {

    @Override
    public List<UarCycleSettingBean> queryList(String uarName) {
        return baseMapper.queryList(uarName);
    }

    @Override
    public List<Map<String, String>> getDistinctList() {
        return this.list(
                new LambdaQueryWrapper<UarCycleSetting>()
                        .orderByDesc(UarCycleSetting::getUarId)
        ).stream().map(item -> Map.of(
                "label", item.getUarName(),
                "value", item.getUarId()
        )).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public boolean setCurrent(String uarId) {
        // 判断是否已经设置为当前
        Integer current = queryIsCurrent(uarId);
        if (current != null && current == 1) {
            throw new RuntimeException("请勿重复设置当前周期");
        }
        // 1. 先重置所有
        baseMapper.resetUarCycleSetting();
        // 2. 设置当前
        UarCycleSetting cycle = new UarCycleSetting();
        cycle.setUarId(uarId);
        cycle.setIsCurrent(1);
        cycle.setUpdateBy(SecurityUtils.getCurrentUsername());
        cycle.setUpdateTime(LocalDateTime.now());
        boolean b = this.updateById(cycle);
        if (!b) {
            throw new RuntimeException("设置失败");
        }
        return true;
    }

    @Override
    @Transactional
    public boolean createCycleSetting(UarCycleSetting uarCycleSetting) {
        validateBusinessFields(uarCycleSetting);
        if (this.getById(uarCycleSetting.getUarId()) != null) {
            throw new RuntimeException("周期ID已存在");
        }
        uarCycleSetting.setCreateBy(SecurityUtils.getCurrentUsername());
        uarCycleSetting.setCreateTime(LocalDateTime.now());
        uarCycleSetting.setIsCurrent(0);
        return this.save(uarCycleSetting);
    }

    @Override
    @Transactional
    public boolean updateCycleSetting(UarCycleSetting uarCycleSetting) {
        validateBusinessFields(uarCycleSetting);
        UarCycleSetting existing = this.getById(uarCycleSetting.getUarId());
        if (existing == null) {
            throw new RuntimeException("周期不存在");
        }

        UarCycleSetting cycle = new UarCycleSetting();
        cycle.setUarId(uarCycleSetting.getUarId());
        cycle.setUarName(uarCycleSetting.getUarName());
        cycle.setDefaultCycleStartDate(uarCycleSetting.getDefaultCycleStartDate());
        cycle.setDefaultCycleEndDate(uarCycleSetting.getDefaultCycleEndDate());
        cycle.setAccessReviewScope(uarCycleSetting.getAccessReviewScope());
        cycle.setUpdateBy(SecurityUtils.getCurrentUsername());
        cycle.setUpdateTime(LocalDateTime.now());
        return this.updateById(cycle);
    }

    private void validateBusinessFields(UarCycleSetting uarCycleSetting) {
        if (uarCycleSetting == null) {
            throw new RuntimeException("周期配置不能为空");
        }
        if (uarCycleSetting.getUarId() == null || uarCycleSetting.getUarId().isEmpty()) {
            throw new RuntimeException("周期ID不能为空");
        }
        if (uarCycleSetting.getUarName() == null || uarCycleSetting.getUarName().isEmpty()) {
            throw new RuntimeException("周期名称不能为空");
        }
        if (uarCycleSetting.getDefaultCycleStartDate() == null
                || uarCycleSetting.getDefaultCycleEndDate() == null) {
            throw new RuntimeException("默认周期起止日期不能为空");
        }
        if (uarCycleSetting.getDefaultCycleEndDate().isBefore(uarCycleSetting.getDefaultCycleStartDate())) {
            throw new RuntimeException("默认周期结束日期不能早于开始日期");
        }
        if (uarCycleSetting.getAccessReviewScope() == null) {
            throw new RuntimeException("权限审核范围不能为空");
        }
    }

    @Override
    public String getCurrentUarId() {
        return baseMapper.getCurrentUarId();
    }

    @Override
    public boolean isCurrentDateWithinCycleRange() {
        return baseMapper.isCurrentDateWithinCycleRange();
    }

    @Override
    public Integer queryIsCurrent(String uarCycleId) {
        return baseMapper.queryIsCurrent(uarCycleId);
    }
}
