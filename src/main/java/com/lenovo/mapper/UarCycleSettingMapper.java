package com.lenovo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lenovo.bean.UarCycleSettingBean;
import com.lenovo.entity.UarCycleSetting;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UarCycleSettingMapper extends BaseMapper<UarCycleSetting> {

    List<UarCycleSettingBean> queryList(String uarName);

    /**
     * 获取当前周期的UarID
     *
     * @return
     */
    String getCurrentUarId();

    boolean isCurrentDateWithinCycleRange();

    Integer queryIsCurrent(String uarCycleId);

    /**
     * 将当前周期置为失效
     * @return
     */
    Integer resetUarCycleSetting();
}
