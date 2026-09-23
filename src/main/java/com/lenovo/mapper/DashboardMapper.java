package com.lenovo.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.DashboardChartReviewBean;
import com.lenovo.bean.DashboardSearchBean;
import com.lenovo.bean.DashboardUARBean;
import com.lenovo.bean.PermissionDistributionBean;
import com.lenovo.bean.PermissionDistributionSearchBean;
import com.lenovo.bean.UseAccessReviewBean;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DashboardMapper {
    Page<DashboardUARBean> queryUarFullData(
            Page<DashboardUARBean> p,
            @Param("useAccessReviewBean") UseAccessReviewBean useAccessReviewBean
    );

    List<DashboardUARBean> exportUarFullData(
            @Param("useAccessReviewBean") UseAccessReviewBean useAccessReviewBean,
            @Param("language") String language
    );

    DashboardChartReviewBean.OverallStatistics queryOverallStatistics(Integer isCurrentCycle, String currentDataCycle, List<String> dataRange);

    Page<DashboardChartReviewBean.OverallList> queryOverallPage(
            Page<DashboardChartReviewBean.OverallList> objectPage,
            @Param("search") DashboardSearchBean searchBean);

    List<DashboardChartReviewBean.OverallList> queryOverallList(@Param("search") DashboardSearchBean searchBean);

    List<DashboardChartReviewBean.LmLevelStatistics> queryLmLevelStatistics(Integer isCurrentCycle, String currentDataCycle, List<String> dataRange);

    List<PermissionDistributionBean.Top10> queryPermissionDistributionTop10(
            @Param("search") PermissionDistributionSearchBean searchBean);

    Long countPermissionDistribution(
            @Param("search") PermissionDistributionSearchBean searchBean);

    Page<PermissionDistributionBean.Detail> queryPermissionDistributionPage(
            Page<PermissionDistributionBean.Detail> page,
            @Param("search") PermissionDistributionSearchBean searchBean);

    List<PermissionDistributionBean.Detail> queryPermissionDistributionList(
            @Param("search") PermissionDistributionSearchBean searchBean);
}
