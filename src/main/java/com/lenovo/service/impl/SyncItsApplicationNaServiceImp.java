package com.lenovo.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.constant.ApiParamsConstant;
import com.lenovo.entity.ItsApplicationData;
import com.lenovo.mapper.ItsApplicationDataMapper;
import com.lenovo.service.SyncItsApplicationService;
import com.lenovo.util.ApiHubUtils;
import com.lenovo.util.RedisUtils;
import com.lenovo.util.SplunkSyncAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Profile("prod-na")
public class SyncItsApplicationNaServiceImp extends ServiceImpl<ItsApplicationDataMapper, ItsApplicationData> implements SyncItsApplicationService {

    private static final int PAGE_STEP = 1000; // 每页数据量


    private final RedisUtils redisUtils;
    private final SplunkSyncAdapter splunkSyncAdapter;

    private final ApiHubUtils apiHubUtils;
    int currentStart = 0; // 起始页初始值
    int currentEnd = PAGE_STEP;
    /**
     * 同步上游应用数据
     */
    @Override
    @Transactional
    public void syncSplunkApplicationData() {
        List<ItsApplicationData> data = null;
        long syncBatchId = System.currentTimeMillis();

        try{
            do {
                String result = apiHubUtils.getNaItsApplicationData(currentStart, currentEnd, ApiParamsConstant.CMDB_APPLICATION_BASE_INFO);
                data = splunkSyncAdapter.getResult(result, ItsApplicationData.class);
                if (!data.isEmpty()) {
                    getBaseMapper().saveOrUpdateBatch(data, syncBatchId);
                }
                currentStart += PAGE_STEP;
            }
            while (!data.isEmpty());
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
