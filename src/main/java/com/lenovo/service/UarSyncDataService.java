package com.lenovo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.ApplicationAccessDataBean;
import com.lenovo.bean.DashboardUARBean;
import com.lenovo.bean.UarSyncDataRequest;
import com.lenovo.entity.ItsApplicationAccessData;

import java.util.List;

public interface UarSyncDataService {
    Page<ApplicationAccessDataBean> getSyncData(UarSyncDataRequest request);

    List<ApplicationAccessDataBean> getSyncDataForExport(UarSyncDataRequest request);

    List<ApplicationAccessDataBean> getDistinctRoleAndBpo(UarSyncDataRequest request);
}
