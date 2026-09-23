package com.lenovo.service.impl;

import cn.hutool.core.date.StopWatch;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.config.ItsApplicationDataAccessLabelEnum;
import com.lenovo.constant.ApiParamsConstant;
import com.lenovo.entity.ItsApplicationAccessData;
import com.lenovo.entity.ItsApplicationData;
import com.lenovo.mapper.ItsApplicationAccessDataServiceMapper;
import com.lenovo.mapper.ItsApplicationDataMapper;
import com.lenovo.service.ItsApplicationAccessDataService;
import com.lenovo.util.ApiHubUtils;
import com.lenovo.util.SplunkSyncAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author mercury
 * @description 针对表【its_application_access_data】的数据库操作Service实现
 * @createDate 2024-10-28 11:38:48
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("prod-na")
public class ItsApplicationAccessDataNaServiceImpl extends BaseItsApplicationAccessDataService
        implements ItsApplicationAccessDataService {

    private final SplunkSyncAdapter splunkSyncAdapter;
    //    private final LineMangerReviewService lineMangerReviewService;
    private static final int PAGE_STEP = 50000; // 每页数据量
    private final ApiHubUtils apiHubUtils;

    /**
     * 全量同步
     *
     * @param currentStart 当前页起始值
     */
    @Override
    public void syncFullData(int currentStart) {
        StopWatch stopWatch = new StopWatch("SYNC_FULL_DATA");
        stopWatch.start();

        int currentEnd = PAGE_STEP; // 终止页初始值
        List<ItsApplicationAccessData> dataList = null;
        if (currentStart == 0) {
            // 重新开始时清空表，currentStart 可能会变
            getBaseMapper().clearTable();   // 清空表
        }
        try {
            do {

                String result = apiHubUtils.getNaItsApplicationData(currentStart, currentEnd, ApiParamsConstant.IAM_ACCESS_DATA);
                dataList = splunkSyncAdapter.getResult(result, ItsApplicationAccessData.class);

                if (!dataList.isEmpty()) {
                    dataList.forEach(data ->
                            data.setAccessLabel(ItsApplicationDataAccessLabelEnum.map(data.getAccessLabel()))
                    );
                    insertParallel(dataList);
                }
                currentStart += PAGE_STEP;
            }
            while (!dataList.isEmpty()); // 终止条件
        } catch (Exception e) {
            log.error("同步失败: currentStart: {}", currentStart, e);
            throw new RuntimeException("同步NA权限全量数据失败", e);
        } finally {
            stopWatch.stop();
            log.info("/n 同步完成: {}", stopWatch.prettyPrint(TimeUnit.SECONDS));
        }

        getBaseMapper().analyzeTable();

    }


}



