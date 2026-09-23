package com.lenovo.mapper;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lenovo.bean.*;
import com.lenovo.dto.ApplicationOperationFocalRequest;
import com.lenovo.dto.ApplicationUarProcessorRequest;
import com.lenovo.entity.ItsApplicationData;
import com.lenovo.security.utils.SecurityUtils;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface ItsApplicationDataMapper  extends BaseMapper<ItsApplicationData> {
    void saveOrUpdateBatch(
            @Param("list") List<ItsApplicationData> list,
            @Param("syncBatchId") long syncBatchId
    );

    void fakeDeleteBySyncBatchId(long syncBatchId);

    List<AdvancedSearchBean> getApplicationMenu(@Param("type") String type, @Param("dataRange") List<String> dataRange);

    int syncLocalApplicationReadyData();

    default int updateUarProcessor(ApplicationUarProcessorRequest bean){
        return update(null,
                new LambdaUpdateWrapper<ItsApplicationData>()
                        .set(ItsApplicationData::getUarProcessor, String.join(",", bean.getItCode()))
                        .set(ItsApplicationData::getUarProcessorEmail, bean.getEmail())
                        .set(ItsApplicationData::getOperatorName, SecurityUtils.getCurrentUsername())
                        .set(ItsApplicationData::getOperatorDate, LocalDateTime.now())
                        .eq(ItsApplicationData::getCmdbId, bean.getCmdbId())
        );
    }

    /**
     * @Description TODO 根据itCode查询是否具备UAR Processer权限
     * @author wangfenglong
     * @date 2025/12/3 15:23
    **/
    List<ItsApplicationData> getApplicationDataByUARProcesser(@Param("bean") UseAccessReviewBean bean);

    /**
     * @Description TODO 根据itCode查询是否具备operation_owner和operation_focal权限
     * @author wangfenglong
     * @date 2026/1/22 12:23
    **/
    List<ItsApplicationData> getApplicationDataByOperationOwnerOrOperationFocal(@Param("bean") UseAccessReviewBean bean);

    default int batchUpdateAppOnlineTime(UarCycleBean uarCycleBean) {
        return update(null,
                new LambdaUpdateWrapper<ItsApplicationData>()
                        .set(ItsApplicationData::getOnlineFlag, true)
                        .set(ItsApplicationData::getOnlineTime, LocalDateTime.now())
                        .set(ItsApplicationData::getModifiedBy, SecurityUtils.getCurrentUsername())
                        .set(ItsApplicationData::getModificationDate, LocalDateTime.now())

                        .in(ItsApplicationData::getCmdbId, uarCycleBean.getCmdbId())
                        .eq(ItsApplicationData::getOnlineFlag, false)
        );
    }

    default int batchUpdateEndMonth(UarCycleBean uarCycleBean) {
        return update(null,
                new LambdaUpdateWrapper<ItsApplicationData>()
                        .set(ItsApplicationData::getOnlineFlag, false)
                        .set(ItsApplicationData::getOfflineTime, LocalDateTime.now())
                        .set(ItsApplicationData::getDecommissionReason, uarCycleBean.getDecommissionReason())
                        .set(ItsApplicationData::getModifiedBy, SecurityUtils.getCurrentUsername())
                        .set(ItsApplicationData::getModificationDate, LocalDateTime.now())

                        .in(ItsApplicationData::getCmdbId, uarCycleBean.getCmdbId())
                        .eq(ItsApplicationData::getOnlineFlag, true)
        );
    }

    int updateDataReadyByCmdbId(@Param("cmdbId") String cmdbId, @Param("source") String source);

    //根据cmdbId查询出需要发送邮件的operation_owner和uar_processor_email
    List<ItsApplicationData> getApplicationDataByCmdbIds(@Param("bean") UarCycleBean uarCycleBean);

    List<Map<String, String>> getDict(String searchKey);

    //获取最新的周期名称
    List<ApplicationCycleInfoBean> queryUarNameLatest(@Param("bean") UarCycleBean uarCycleBean);

    default int updateOperationFocal(ApplicationOperationFocalRequest bean){
        return update(null,
                new LambdaUpdateWrapper<ItsApplicationData>()
                        .set(ItsApplicationData::getOperationFocal, bean.getItCode())
                        .eq(ItsApplicationData::getCmdbId, bean.getCmdbId())
        );
    }

    ItsApplicationData findByCmdbId(String cmdbId);

    List<AdvancedSearchBean> getFocal(
            @Param("dataRange") List<String> dataRange
    );

    List<String> getSAndAEmailsByCmdbIds(@Param("bean") UarCycleBean uarCycleBean);

    List<AdvancedSearchBean> getSelectorOptions(
            @Param("scopeSql") String scopeSql,
            @Param("fieldName") String fieldName,
            @Param("currentDataCycle") String currentDataCycle,
            @Param("dataRange") List<String> dataRange,
            @Param("filterOnlineApplications") boolean filterOnlineApplications
    );
}
