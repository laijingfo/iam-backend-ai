package com.lenovo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.constant.ApiParamsConstant;
import com.lenovo.entity.ItsApplicationData;
import com.lenovo.mapper.ItsApplicationDataMapper;
import com.lenovo.security.utils.RoleUtils;
import com.lenovo.service.SyncItsApplicationService;
import com.lenovo.service.UarCycleSettingService;
import com.lenovo.util.RedisUtils;
import com.lenovo.util.SplunkSyncAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-08-21 14:59
 * @project iam-backend
 * @description
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Profile("!prod-na")
public class SyncItsApplicationServiceImpl extends ServiceImpl<ItsApplicationDataMapper, ItsApplicationData> implements SyncItsApplicationService {

    private static final int PAGE_STEP = 1000; // 每页数据量


    private final RedisUtils redisUtils;
    private final SplunkSyncAdapter splunkSyncAdapter;

    @Override
    @Transactional
    public void syncSplunkApplicationData() {

        int currentStart = 0; // 起始页初始值
        int currentEnd = PAGE_STEP; // 终止页初始值
        List<ItsApplicationData> data = null;
        long syncBatchId = System.currentTimeMillis();

        try{
            do {
                String result = splunkSyncAdapter.makeRequest(splunkSyncAdapter.splunkAddress + ApiParamsConstant.SPLUNK_IAM_PATH,
                        ApiParamsConstant.CMDB_APPLICATION_BASE_INFO, ApiParamsConstant.SYNC_EXEC_MODE, currentStart, currentEnd);
                data = splunkSyncAdapter.getResult(result, ItsApplicationData.class);

                if (!data.isEmpty()) {
                    getBaseMapper().saveOrUpdateBatch(data, syncBatchId);
                }
                currentStart += PAGE_STEP;
            }
            while (!data.isEmpty()); // 终止条件

            // 把未同步的数据标记删除（update）
//            getBaseMapper().fakeDeleteBySyncBatchId(syncBatchId);

            try {
                redisUtils.deleteFolder("dict");
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                e.printStackTrace();
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
    }
}
