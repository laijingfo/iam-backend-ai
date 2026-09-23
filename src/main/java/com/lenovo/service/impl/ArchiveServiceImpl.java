package com.lenovo.service.impl;

import com.lenovo.entity.SendEmailActionLog;
import com.lenovo.mapper.ArchiveMapper;
import com.lenovo.mapper.SendEmailActionLogMapper;
import com.lenovo.mapper.UarCycleSettingMapper;
import com.lenovo.service.ArchiveService;
import com.lenovo.util.I18nUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

/**
 * @Description TODO 归档数据服务实现类
 * @ClassName ArchiveServiceImpl
 * @Author wangfenglong
 * @Date 2026/3/30 15:58
 **/
@Log4j2
@Service
@RequiredArgsConstructor
public class ArchiveServiceImpl implements ArchiveService
{
    private final ArchiveMapper archiveMapper;
    private final SendEmailActionLogMapper sendEmailActionLogMapper;
    private final UarCycleSettingMapper uarCycleSettingMapper;

    @Lazy
    @Autowired
    private ArchiveServiceImpl self;

    @Async
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveData(String itCode, String maintenanceId)
    {
        archiveMapper.insertMaintenanceBySelect(maintenanceId, itCode);
        archiveMapper.insertReviewBySelect(maintenanceId, itCode);
        archiveMapper.insertAlertBySelect(maintenanceId, itCode);
        checkDataCount(itCode, maintenanceId);
        archiveMapper.truncateTable();
        // 重置周期设置
        uarCycleSettingMapper.resetUarCycleSetting();
    }

    /**
     * 数据量校验
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void checkDataCount(String itCode, String maintenanceId)
    {
        Long maintenanceCount = archiveMapper.selectMaintenanceCount();
        Long maintenanceHistoryCount = archiveMapper.selectMaintenanceHistoryCount(maintenanceId);

        Long reviewCount = archiveMapper.selectReviewCount();
        Long reviewHistoryCount = archiveMapper.selectReviewHistoryCount(maintenanceId);

        Long alertCount = archiveMapper.selectAlertCount();
        Long alertHistoryCount = archiveMapper.selectAlertHistoryCount(maintenanceId);

        if (!maintenanceCount.equals(maintenanceHistoryCount) || !reviewCount.equals(reviewHistoryCount) || !alertCount.equals(alertHistoryCount))
        {
            self.saveErrorLog(itCode,"归档后数据不一致！已全部回滚:" + "maintenance表：" + maintenanceCount + " → " + maintenanceHistoryCount + " | review表：" + reviewCount + " → " + reviewHistoryCount + " | alert表：" + alertCount + " → " + alertHistoryCount);
            throw new RuntimeException(I18nUtil.get("custom.archive.error3"));
        }
        log.info("数据校验通过：主表与历史表数据量一致{}", maintenanceCount + " → " + maintenanceHistoryCount + " | review表：" + reviewCount + " → " + reviewHistoryCount + " | alert表：" + alertCount + " → " + alertHistoryCount);
    }

    /**
     * 独立新事务：保存错误日志
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveErrorLog(String itCode, String errorMsg)
    {
        SendEmailActionLog log = new SendEmailActionLog();
        log.setItCode(itCode);
        log.setOperation("archiveData" + "-UAR周期设置:归档数据");
        log.setSendFlag("archiveData");
        log.setMessage(errorMsg);
        log.setStackTrace("");
        log.setBatchNo("");
        log.setCreateDate(LocalDateTime.now());
        sendEmailActionLogMapper.addSendEmailLog(log);
    }
}
