package com.lenovo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.DashboardChartReviewBean;
import com.lenovo.bean.DashboardSearchBean;
import com.lenovo.bean.DashboardUARBean;
import com.lenovo.bean.PermissionDistributionBean;
import com.lenovo.bean.PermissionDistributionSearchBean;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.mapper.DashboardMapper;
import com.lenovo.service.DashboardService;
import com.lenovo.service.RegionalUarPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class DashboardServiceImpl implements DashboardService {

    private final DashboardMapper dashboardMapper;
    private final RegionalUarPolicyService regionalUarPolicyService;


    @Override
    public Page<DashboardUARBean> queryUarFullData(Integer page, Integer size, UseAccessReviewBean useAccessReviewBean) {
        try {
            Page<DashboardUARBean> result = dashboardMapper.queryUarFullData(
                    new Page<>(page, size),
                    useAccessReviewBean
            );
            return result;
        } catch (Exception e) {
            log.error("查询历史记录失败", e);
            throw new RuntimeException("查询历史记录失败: " + e.getMessage());
        }
    }

    @Override
    public List<DashboardUARBean> exportUarFullData(UseAccessReviewBean useAccessReviewBean, String language) {
        List<DashboardUARBean> exportData = dashboardMapper.exportUarFullData(useAccessReviewBean, language);
        exportData.forEach(item -> item.setAccessLabel(
                regionalUarPolicyService.formatAccessLabelForExport(item.getAccessLabel(), language)
        ));
        return exportData;
    }

    @Override
    public DashboardChartReviewBean.OverallStatistics queryOverallStatistics(Integer isCurrentCycle, String currentDataCycle, List<String> dataRange) {
        return dashboardMapper.queryOverallStatistics(isCurrentCycle, currentDataCycle, dataRange);
    }

    @Override
    public Page<DashboardChartReviewBean.OverallList> queryOverallPage(DashboardSearchBean searchBean) {
        return dashboardMapper.queryOverallPage(new Page<>(searchBean.getPage(), searchBean.getSize()), searchBean);
    }

    @Override
    public List<DashboardChartReviewBean.OverallList> queryOverallList(DashboardSearchBean searchBean) {
        return dashboardMapper.queryOverallList(searchBean);
    }

    @Override
    public Map<String, Map<String, String>> queryLmLevelStatistics(Integer isCurrentCycle, String currentDataCycle, List<String> dataRange) {
        List<DashboardChartReviewBean.LmLevelStatistics> mapList = dashboardMapper.queryLmLevelStatistics(isCurrentCycle, currentDataCycle, dataRange);
        Map<String, Map<String, String>> result = new HashMap<>();
        for (DashboardChartReviewBean.LmLevelStatistics item : mapList) {
            double completedRate = (item.getCompletedPerson() == null || item.getCompletedPerson() == 0) ? 0.0 : Math.round(item.getCompletedPerson() * 10000.0 / item.getTotalPerson()) / 100.0;

            result.put(item.getLevel(),
                    Map.of(
                            "totalPerson", item.getTotalPerson().toString(), "totalItems", item.getTotalItems().toString(),
                            "completedPerson", item.getCompletedPerson().toString(), "uncompletedPerson", item.getUncompletedPerson().toString(),
                            "completedRate", completedRate + "%"
                    )
            );
        }


        return result;
    }

    @Override
    public List<PermissionDistributionBean.Top10> queryPermissionDistributionTop10(
            PermissionDistributionSearchBean searchBean) {
        return dashboardMapper.queryPermissionDistributionTop10(searchBean);
    }

    @Override
    public Page<PermissionDistributionBean.Detail> queryPermissionDistributionPage(
            PermissionDistributionSearchBean searchBean) {
        long total = dashboardMapper.countPermissionDistribution(searchBean);
        Page<PermissionDistributionBean.Detail> page =
                new Page<>(searchBean.getPage(), searchBean.getSize(), total, false);
        if (total == 0) {
            return page;
        }
        return dashboardMapper.queryPermissionDistributionPage(page, searchBean);
    }

    @Override
    public List<PermissionDistributionBean.Detail> queryPermissionDistributionList(
            PermissionDistributionSearchBean searchBean) {
        return dashboardMapper.queryPermissionDistributionList(searchBean);
    }
}
