package com.lenovo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.ApplicationAccessDataBean;
import com.lenovo.bean.DashboardUARBean;
import com.lenovo.bean.UarSyncDataRequest;
import com.lenovo.entity.ItsApplicationAccessData;
import com.lenovo.mapper.UarSyncDataMapper;
import com.lenovo.service.UarSyncDataService;
import com.lenovo.service.RegionalUarPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UarSyncDataServiceImpl implements UarSyncDataService {

    private final UarSyncDataMapper uarSyncDataMapper;
    private final RegionalUarPolicyService regionalUarPolicyService;

    @Override
    public Page<ApplicationAccessDataBean>  getSyncData(UarSyncDataRequest request) {
        Page<ApplicationAccessDataBean> page = new Page<>(request.getPage(), request.getSize());
        return uarSyncDataMapper.getSyncData(page, request);
    }

    @Override
    public List<ApplicationAccessDataBean> getSyncDataForExport(UarSyncDataRequest request) {
        List<ApplicationAccessDataBean> list = uarSyncDataMapper.getSyncDataForExport(request);
        formatAccessLabels(list, request.getLanguage());
        return list;
    }

    @Override
    public List<ApplicationAccessDataBean> getDistinctRoleAndBpo(UarSyncDataRequest request) {
        List<ApplicationAccessDataBean> list = uarSyncDataMapper.getDistinctRoleAndBpo(request);
        formatAccessLabels(list, request.getLanguage());
        return list;
    }

    private void formatAccessLabels(List<ApplicationAccessDataBean> list, String language) {
        list.forEach(item -> item.setAccessLabel(
                regionalUarPolicyService.formatAccessLabelForExport(item.getAccessLabel(), language)
        ));
    }
}
