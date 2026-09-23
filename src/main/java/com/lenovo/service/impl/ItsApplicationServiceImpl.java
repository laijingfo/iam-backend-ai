package com.lenovo.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.bean.AdvancedSearchBean;
import com.lenovo.dto.ApplicationOperationFocalRequest;
import com.lenovo.dto.ApplicationUarProcessorRequest;
import com.lenovo.constant.ApiParamsConstant;
import com.lenovo.constant.SceneSelectorConstant;
import com.lenovo.entity.ItsApplicationData;
import com.lenovo.mapper.ItsApplicationDataMapper;
import com.lenovo.security.exception.BadRequestException;
import com.lenovo.security.utils.RoleUtils;
import com.lenovo.service.ItsApplicationService;
import com.lenovo.service.UarCycleSettingService;
import com.lenovo.util.RedisUtils;
import com.lenovo.util.SplunkSyncAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItsApplicationServiceImpl extends ServiceImpl<ItsApplicationDataMapper, ItsApplicationData> implements ItsApplicationService {

    private static final String APPLICATION_REDIS_KEY = "dict:application:";
    private static final int PAGE_STEP = 1000; // 每页数据量


    private final RedisUtils redisUtils;
    private final RoleUtils roleUtils;
    private final UarCycleSettingService uarCycleSettingService;
    private final SplunkSyncAdapter splunkSyncAdapter;


    /**
     * 同步上游应用数据
     */
//    @Override
//    @Transactional
//    public void syncSplunkApplicationData() {
//
//        int currentStart = 0; // 起始页初始值
//        int currentEnd = PAGE_STEP; // 终止页初始值
//        List<ItsApplicationData> data = null;
//        long syncBatchId = System.currentTimeMillis();
//
//        try{
//            do {
//                String result = splunkSyncAdapter.makeRequest(splunkSyncAdapter.splunkAddress + ApiParamsConstant.SPLUNK_IAM_PATH,
//                        ApiParamsConstant.CMDB_APPLICATION_BASE_INFO, ApiParamsConstant.SYNC_EXEC_MODE, currentStart, currentEnd);
//                data = splunkSyncAdapter.getResult(result, ItsApplicationData.class);
//
//                if (!data.isEmpty()) {
//                    getBaseMapper().saveOrUpdateBatch(data, syncBatchId);
//                }
//                currentStart += PAGE_STEP;
//            }
//            while (!data.isEmpty()); // 终止条件
//
//            // 把未同步的数据标记删除（update）
////            getBaseMapper().fakeDeleteBySyncBatchId(syncBatchId);
//
//            try {
//                redisUtils.deleteFolder("dict");
//            } catch (Exception e) {
//                log.error(e.getMessage(), e);
//                e.printStackTrace();
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            throw new RuntimeException(e.getMessage());
//        }
//    }

    /**
     * 同步本地应用的数据准备状态
     */
    @Override
    @Transactional
    public void syncLocalApplicationReadyData() {
        getBaseMapper().syncLocalApplicationReadyData();

    }

    @Override
    public List<AdvancedSearchBean> getApplicationMenu(String type, List<String> dataRange) {
        String cacheKey = APPLICATION_REDIS_KEY + type;
        // 获取缓存
        if (redisUtils.hasKey(cacheKey) && dataRange.contains("*"))
            return JSON.parseObject((String) redisUtils.get(cacheKey), new TypeReference<List<AdvancedSearchBean>>() {});

        // 放到缓存中，每天同步上游应用数据会清空缓存
        List<AdvancedSearchBean> data = getBaseMapper().getApplicationMenu(type, dataRange);
        if (data.isEmpty())
            return Collections.emptyList();

        // 全量数据放入缓存
        if (dataRange.contains("*"))
            redisUtils.set(cacheKey, JSON.toJSONString(data));

        return data;
    }

    @Override
    public int updateUarProcessor(ApplicationUarProcessorRequest bean) {
        return getBaseMapper().updateUarProcessor(bean);
    }

    @Override
    public int updateOperationFocal(ApplicationOperationFocalRequest bean) {
        return getBaseMapper().updateOperationFocal(bean);
    }

    @Override
    public List<AdvancedSearchBean> getFocal(List<String> dataRange) {
        List<AdvancedSearchBean> focal = getBaseMapper().getFocal(dataRange);
        focal.add(0, new AdvancedSearchBean("Blank(No S&A)", "None"));
        return focal;
    }

    @Override
    public List<AdvancedSearchBean> getSelectorOptions(String sceneCode, String fieldCode, String currentDataCycle) {
        SceneSelectorConstant.Scene scene = SceneSelectorConstant.Scene.fromCode(sceneCode);
        SceneSelectorConstant.Field field = SceneSelectorConstant.Field.fromCode(fieldCode);
        if (!scene.supports(field)) {
            throw new BadRequestException("The current business scenario does not support this field");
        }

        List<String> dataRange = roleUtils.getCurrentUserUarBusinessDataRange();
        if (dataRange == null || dataRange.isEmpty()) {
            return Collections.emptyList();
        }

        String scopeSql = scene.getScopeSql();
        // 处理dashboard场景
        if (scene == SceneSelectorConstant.Scene.DASHBOARD) {
            if (currentDataCycle == null || currentDataCycle.trim().isEmpty()) {
                throw new BadRequestException("The dashboard option must specify cycle");
            }
            Integer isCurrent = uarCycleSettingService.queryIsCurrent(currentDataCycle);
            if (isCurrent == null) {
                throw new BadRequestException("Cycle does not exist");
            }
            scopeSql = SceneSelectorConstant.dashboardScopeSql(isCurrent == 1 ,currentDataCycle);
        }

        // 获取数据
        List<AdvancedSearchBean> selectorOptions = getBaseMapper().getSelectorOptions(
                scopeSql,
                field.getColumn(),
                currentDataCycle,
                dataRange,
                scene.shouldFilterOnlineApplications()
        );


        // 后置处理
        if (field == SceneSelectorConstant.Field.OPERATION_FOCAL) {
            selectorOptions.add(0, new AdvancedSearchBean("Blank(No S&A)", "None"));
        }

        return selectorOptions;
    }

}
