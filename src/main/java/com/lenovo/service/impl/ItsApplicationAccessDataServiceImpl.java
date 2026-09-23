package com.lenovo.service.impl;

import cn.hutool.core.date.StopWatch;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.constant.ApiParamsConstant;
import com.lenovo.entity.ItsApplicationAccessData;
import com.lenovo.mapper.ItsApplicationAccessDataServiceMapper;
import com.lenovo.mapper.ItsApplicationDataMapper;
import com.lenovo.service.ItsApplicationAccessDataService;
import com.lenovo.util.SplunkSyncAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * @author mercury
 * @description 针对表【its_application_access_data】的数据库操作Service实现
 * @createDate 2024-10-28 11:38:48
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!prod-na")
public class ItsApplicationAccessDataServiceImpl extends BaseItsApplicationAccessDataService
        implements ItsApplicationAccessDataService {

    private final SplunkSyncAdapter splunkSyncAdapter;
    private final ItsApplicationDataMapper itsApplicationDataMapper;
    //    private final LineMangerReviewService lineMangerReviewService;
    private static final int PAGE_STEP = 50000; // 每页数据量


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
        List<ItsApplicationAccessData> data = null;
        if (currentStart == 0) {
            // 重新开始时清空表，currentStart 可能会变
            getBaseMapper().clearTable();   // 清空表
        }
        try {
            do {

                String result = splunkSyncAdapter.makeRequest(splunkSyncAdapter.splunkAddress + ApiParamsConstant.SPLUNK_IAM_PATH,
                        ApiParamsConstant.IAM_ACCESS_DATA, ApiParamsConstant.SYNC_EXEC_MODE, currentStart, currentEnd);
                data = splunkSyncAdapter.getResult(result, ItsApplicationAccessData.class);

                if (!data.isEmpty()) {
                    insertParallel(data);
                }
                currentStart += PAGE_STEP;
            }
            while (!data.isEmpty()); // 终止条件
        } catch (Exception e) {
            log.error("同步失败: currentStart: {}", currentStart);
            e.printStackTrace();
        } finally {
            stopWatch.stop();
            log.info("/n 同步完成: {}", stopWatch.prettyPrint(TimeUnit.SECONDS));
        }

        getBaseMapper().analyzeTable();

    }

    /**
     * 每批多少条
     */
    private static final int BATCH_SIZE = 500;

    /**
     * 并行插入
     */
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);

    @Transactional(rollbackFor = Exception.class)
    public void insertParallel(List<ItsApplicationAccessData> total) throws Exception {
        // 1. 分片
        List<List<ItsApplicationAccessData>> parts = new ArrayList<>();
        for (int start = 0; start < total.size(); start += BATCH_SIZE) {
            parts.add(total.subList(start, Math.min(start + BATCH_SIZE, total.size())));
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (List<ItsApplicationAccessData> part : parts) {
            CompletableFuture<Void> f =
                    CompletableFuture.runAsync(() -> batchInsert(part), POOL);
            futures.add(f);
        }

        // 2. 等待全部完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    public void batchInsert(List<ItsApplicationAccessData> list) {
/*
        // 这段代码用来验证字段长度，只有报错了才开启校验
        for (ItsApplicationAccessData item : list) {
            checkLength("application", item.getApplication(), 255);
            checkLength("department", item.getDepartment(), 255);
            checkLength("lineManagerEmail", item.getLineManagerEmail(), 255);
            checkLength("secondLineManager", item.getSecondLineManager(), 255);
            checkLength("bpoBandEdFlag", item.getBpoBandEdFlag(), 255);
            checkLength("backupBpoBandEdFlag", item.getBackupBpoBandEdFlag(), 255);
            checkLength("appBpoBandEdFlag", item.getAppBpoBandEdFlag(), 255);
            checkLength("lineManagerBandEdFlag", item.getLineManagerBandEdFlag(), 255);
            checkLength("userRealName", item.getUserRealName(), 255);
            checkLength("systemRoleType", item.getSystemRoleType(), 255);
            checkLength("applyFormRoleState", item.getApplyFormRoleState(), 255);
        }*/


        getBaseMapper().batchInsert(list);

    }

    /**
     * 检查字段长度
     */
    private void checkLength(String field, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            log.error("字段超长: field={}, length={}, max={}, value={}",
                    field, value.length(), maxLength, value);
        }
    }

}



