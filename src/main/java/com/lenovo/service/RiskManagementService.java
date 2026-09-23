package com.lenovo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.*;
import com.lenovo.entity.RiskManagement;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lenovo.entity.Role;

import java.util.List;
import java.util.Map;

/**
* @author mercury
* @description 针对表【risk_management】的数据库操作Service
* @createDate 2024-10-28 11:38:48
*/
public interface RiskManagementService extends IService<RiskManagement> {

    void processSync();

    Page<RiskManagement> query(RiskQueryBean riskQueryBean, Integer page, Integer size);

    /**
     * 统计数量
     * @return
     */
    RiskManagementStatisticsBean riskManagementStatistics();

    /**
     *
     * 应用程序下拉选
     * @return
     */

    List<RiskManagementApplicationBean> applicationList();


    public List<RiskManagementcomplianceStatusBean> complianceStatusList();

    public Map<String,List<AdvancedSearchBean>>  AdvancedSearchBeanList();

       List<String> findAppOwnerEmail();


    public List<RiskManagement> getByIdTSI(Integer id);

}
