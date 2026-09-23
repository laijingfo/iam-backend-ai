package com.lenovo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.ApplicationAccessDataBean;
import com.lenovo.bean.DashboardUARBean;
import com.lenovo.bean.UarSyncDataRequest;
import com.lenovo.entity.ItsApplicationAccessData;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UarSyncDataMapper extends BaseMapper<ItsApplicationAccessData> {
    Page<ApplicationAccessDataBean> getSyncData(Page<ApplicationAccessDataBean> page, @Param("request") UarSyncDataRequest request);

    List<ApplicationAccessDataBean> getSyncDataForExport(@Param("request") UarSyncDataRequest request);

    List<ApplicationAccessDataBean> getDistinctRoleAndBpo(@Param("request") UarSyncDataRequest request);
}
