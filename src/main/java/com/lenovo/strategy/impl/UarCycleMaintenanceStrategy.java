package com.lenovo.strategy.impl;

import com.lenovo.bean.UarCycleBean;
import com.lenovo.dto.BatchSendRequest;
import com.lenovo.entity.SendEmailActionLog;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.service.SendEmailActionLogService;
import com.lenovo.service.UarCycleMaintenanceService;
import com.lenovo.strategy.CheckStrategy;
import com.lenovo.strategy.SendEmailStrategyType;
import com.lenovo.util.I18nUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @Description TODO 周期设置发送邮件
 * @ClassName UarCycleMaintenanceStrategy
 * @Author wangfenglong
 * @Date 2026/4/22 16:40
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class UarCycleMaintenanceStrategy implements CheckStrategy<UarCycleBean,ResponseEntity<?>>
{
    private final UarCycleMaintenanceService uarCycleMaintenanceService;
    private final SendEmailActionLogService sendEmailActionLogService;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

    @Override
    public ResponseEntity<?> handle(BatchSendRequest request, UarCycleBean uarCycleBean)
    {
        String sendFlag = uarCycleBean.getCycleDataFlag();
        String itCode = SecurityUtils.getCurrentUserId();
        String batchNo = generateBatchNo(itCode,sendFlag);
        String taskId = String.format("%s_%s_%s",sendFlag,itCode, UUID.randomUUID().toString().replace("-", ""));
        log.info("UAR周期设置:通知邮件：发送标志sendFlag:{},开始提交异步任务,任务ID：{}", sendFlag,taskId);
        uarCycleMaintenanceService.batchSendEmailsAsyncByOnlineTimeAndOfflineTime(uarCycleBean, itCode,sendFlag,batchNo)
        .exceptionally(throwable ->
        {
            log.error("UAR周期设置:通知邮件：发送标志sendFlag:{},异步任务执行失败,任务ID：{}", sendFlag,taskId, throwable);
            try
            {
                SendEmailActionLog sendEmailActionLog = new SendEmailActionLog();
                sendEmailActionLog.setItCode(itCode);
                sendEmailActionLog.setOperation("UAR周期设置:通知邮件");
                sendEmailActionLog.setSendFlag(sendFlag);
                sendEmailActionLog.setMessage(throwable.getMessage());
                sendEmailActionLog.setStackTrace(Arrays.toString(throwable.getStackTrace()));
                sendEmailActionLog.setBatchNo(batchNo);
                sendEmailActionLog.setCreateDate(LocalDateTime.now());
                sendEmailActionLogService.addSendEmailLog(sendEmailActionLog);
            }
            catch (Exception e)
            {
                log.error("UAR周期设置:通知邮件：发送标志sendFlag:{},存储异常信息到数据库失败，任务ID：{}", sendFlag,taskId, e);
            }
            return null;
        });
        return ResponseEntity.ok(Map.of("success", true, "message", I18nUtil.get("common.success")));
    }

    private String generateBatchNo(String itCode,String sendFlag)
    {
        String batchNo;
        try
        {
            String currentDate = LocalDateTime.now().format(formatter);
            int randomNum = 10000 + ThreadLocalRandom.current().nextInt(90000);
            if (itCode == null || itCode.trim().isEmpty())
            {
                itCode = "DEFAULT_" + ThreadLocalRandom.current().nextInt(100000);
                log.error("当前用户IT编码为空，使用默认值：{}", itCode);
            }
            else
            {
                itCode = itCode.trim(); // 去除首尾空格，避免无效字符
            }
            //拼接批次号（格式：上下线标志_IT编码_时间戳_5位随机数）
            batchNo = String.format("%s_%s_%s_%d", sendFlag ,itCode, currentDate, randomNum);
        }
        catch (Exception e)
        {
            log.error("生成批次号异常", e);
            batchNo = UUID.randomUUID().toString().replace("-", "");
        }
        return batchNo;
    }

    @Override
    public SendEmailStrategyType getType()
    {
        return SendEmailStrategyType.UAR_CYCLE_MAINTENANCE_TYPE;
    }
}
