package com.lenovo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.DashboardChartReviewBean;
import com.lenovo.bean.DashboardSearchBean;
import com.lenovo.bean.DashboardUARBean;
import com.lenovo.bean.PermissionDistributionBean;
import com.lenovo.bean.PermissionDistributionSearchBean;
import com.lenovo.bean.UseAccessReviewBean;

import java.util.List;
import java.util.Map;

public interface DashboardService {
    Page<DashboardUARBean> queryUarFullData(Integer page, Integer size, UseAccessReviewBean useAccessReviewBean);

    List<DashboardUARBean> exportUarFullData(UseAccessReviewBean useAccessReviewBean, String language);

    DashboardChartReviewBean.OverallStatistics queryOverallStatistics(Integer isCurrentCycle, String currentDataCycle, List<String> dataRange);

    Page<DashboardChartReviewBean.OverallList> queryOverallPage(DashboardSearchBean searchBean);

    List<DashboardChartReviewBean.OverallList> queryOverallList(DashboardSearchBean searchBean);

    Map<String, Map<String, String>> queryLmLevelStatistics(Integer isCurrentCycle, String currentDataCycle, List<String> dataRange);

    List<PermissionDistributionBean.Top10> queryPermissionDistributionTop10(PermissionDistributionSearchBean searchBean);

    Page<PermissionDistributionBean.Detail> queryPermissionDistributionPage(PermissionDistributionSearchBean searchBean);

    List<PermissionDistributionBean.Detail> queryPermissionDistributionList(PermissionDistributionSearchBean searchBean);
}
