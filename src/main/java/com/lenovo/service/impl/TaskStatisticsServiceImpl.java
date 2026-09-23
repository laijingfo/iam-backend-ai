package com.lenovo.service.impl;

import com.lenovo.bean.RiskManagementStatisticsBean;
import com.lenovo.bean.TaskStatisticsBean;
import com.lenovo.security.utils.RoleUtils;
import com.lenovo.service.BPOService;
import com.lenovo.service.LineMangerReviewService;
import com.lenovo.service.TaskStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TaskStatisticsServiceImpl implements TaskStatisticsService {

    private final LineMangerReviewService lineMangerReviewService;
    private final BPOService bpoService;
    private final RoleUtils roleUtils;

    @Override
    public TaskStatisticsBean getTaskStatistics() {
        List<String> lineManagerDataRange = roleUtils.getCurrentUserPersonalBusinessDataRangeOfMgr();
        Map<String, List<String>> bpoDataRange = roleUtils.getCurrentUserPersonalBusinessDataRangeOfBpo();

        RiskManagementStatisticsBean lineManagerStatistics =
                lineMangerReviewService.UseAccessReviewStatistics(lineManagerDataRange);
        RiskManagementStatisticsBean bpoStatistics = bpoService.BPOStatistics(bpoDataRange);

        int lineManagerPending = pending(lineManagerStatistics);
        int bpoPending = pending(bpoStatistics);
        int lineManagerCompleted = completed(lineManagerStatistics);
        int bpoCompleted = completed(bpoStatistics);

        return new TaskStatisticsBean(
                new TaskStatisticsBean.TaskCount(
                        lineManagerPending + bpoPending,
                        lineManagerPending,
                        bpoPending
                ),
                new TaskStatisticsBean.TaskCount(
                        lineManagerCompleted + bpoCompleted,
                        lineManagerCompleted,
                        bpoCompleted
                )
        );
    }

    private int pending(RiskManagementStatisticsBean statistics) {
        return statistics == null || statistics.getPendingProcessing() == null
                ? 0 : statistics.getPendingProcessing();
    }

    private int completed(RiskManagementStatisticsBean statistics) {
        return statistics == null || statistics.getCompleted() == null
                ? 0 : statistics.getCompleted();
    }
}
