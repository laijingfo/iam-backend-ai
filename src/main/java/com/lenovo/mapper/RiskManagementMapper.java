package com.lenovo.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.*;
import com.lenovo.entity.RiskManagement;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* @author mercury
* @description 针对表【risk_management】的数据库操作Mapper
* @createDate 2024-10-28 11:38:48
* @Entity com.lenovo.entity.RiskManagement
*/
public interface RiskManagementMapper extends BaseMapper<RiskManagement> {

    void processSync();

    Page<RiskManagement> query(Page p, @Param("riskQueryBean") RiskQueryBean riskQueryBean);

    /**
     * 统计数量
     * @return
     */
    RiskManagementStatisticsBean riskManagementStatistics();


    List<RiskManagementApplicationBean> applicationList();

    List<RiskManagementcomplianceStatusBean> complianceStatusList();

    List<AdvancedSearchBean> appOwnerDomainList();

    List<AdvancedSearchBean> appOwnerT2OrgList();

    List<AdvancedSearchBean> appOwnerT3OrgList();

    List<AdvancedSearchBean> appOwnerT4OrgList();

    List<AdvancedSearchBean> ComplianceIDList();

    List<AdvancedSearchBean> RiskHandlingAssigneeList();

    List<AdvancedSearchBean> RiskHandlingAssigneeT2OrgList();

    List<AdvancedSearchBean> RiskHandlingAssigneeT3OrgList();

    List<AdvancedSearchBean> RiskHandlingAssigneeT4OrgList();

    List<String> findAppOwnerEmail();
    List<RiskManagement> getByIdTSI(Integer id);


}




