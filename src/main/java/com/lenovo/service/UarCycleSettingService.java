package com.lenovo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lenovo.bean.UarCycleSettingBean;
import com.lenovo.entity.UarCycleSetting;

import java.util.List;
import java.util.Map;

public interface UarCycleSettingService extends IService<UarCycleSetting> {
    List<UarCycleSettingBean> queryList(String uarName);

    List<Map<String, String>> getDistinctList();

    boolean setCurrent(String uarId);

    boolean createCycleSetting(UarCycleSetting uarCycleSetting);

    boolean updateCycleSetting(UarCycleSetting uarCycleSetting);

    String getCurrentUarId();

    boolean isCurrentDateWithinCycleRange();

    /**
     * 查询周期是否是当前周期
     * @param uarCycleId
     * @return
     */
    Integer queryIsCurrent(String uarCycleId);
}
