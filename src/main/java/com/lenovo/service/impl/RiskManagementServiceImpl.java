package com.lenovo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.bean.*;
import com.lenovo.entity.RiskManagement;
import com.lenovo.service.RiskManagementService;
import com.lenovo.mapper.RiskManagementMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author mercury
 * @description 针对表【risk_management】的数据库操作Service实现
 * @createDate 2024-10-28 11:38:48
 */
@Service
public class RiskManagementServiceImpl extends ServiceImpl<RiskManagementMapper, RiskManagement>
        implements RiskManagementService {

    @Override
    public void processSync() {

        getBaseMapper().processSync();

    }

    @Override
    public Page<RiskManagement> query(RiskQueryBean riskQueryBean, Integer page, Integer size) {
        Page p = new Page(page, size);
        return getBaseMapper().query(p, riskQueryBean);
    }

    @Override
    public RiskManagementStatisticsBean riskManagementStatistics() {
        return getBaseMapper().riskManagementStatistics();
    }

    @Override
    public List<RiskManagementApplicationBean> applicationList() {
        return getBaseMapper().applicationList();
    }

    public List<RiskManagementcomplianceStatusBean> complianceStatusList() {
        return getBaseMapper().complianceStatusList();
    }

    public   Map<String,List<AdvancedSearchBean>> AdvancedSearchBeanList() {
        Map <String,List<AdvancedSearchBean>> map=new HashMap<>();
        map.put("appOwnerDomainList",getBaseMapper().appOwnerDomainList());
        map.put("appOwnerT2OrgList",getBaseMapper().appOwnerT2OrgList());
        map.put("appOwnerT3OrgList",getBaseMapper().appOwnerT3OrgList());
        map.put("appOwnerT4OrgList",getBaseMapper().appOwnerT4OrgList());
        map.put("ComplianceIDList",getBaseMapper().ComplianceIDList());
        map.put("RiskHandlingAssigneeList",getBaseMapper().RiskHandlingAssigneeList());
        map.put("RiskHandlingAssigneeT2OrgList",getBaseMapper().RiskHandlingAssigneeT2OrgList());
        map.put("RiskHandlingAssigneeT3OrgList",getBaseMapper().RiskHandlingAssigneeT3OrgList());
        map.put("RiskHandlingAssigneeT4OrgList",getBaseMapper().RiskHandlingAssigneeT4OrgList());

        return map;
    }

    @Override
    public List<String> findAppOwnerEmail() {
        return getBaseMapper().findAppOwnerEmail();
    }

    @Override
    public List<RiskManagement> getByIdTSI(Integer id) {
        return getBaseMapper().getByIdTSI(id);
    }

}




