package com.lenovo.strategy.impl;

import com.lenovo.bean.ExpiredBpoBean;
import com.lenovo.dto.BatchSendRequest;
import com.lenovo.dto.UarAlertRequest;
import com.lenovo.mapper.AlertDashboardMapper;
import com.lenovo.service.AlertDashboardService;
import com.lenovo.service.UarMailTemplateService;
import com.lenovo.strategy.CheckStrategy;
import com.lenovo.strategy.SendEmailStrategyType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @Description TODO 给失效的bpo发送focal邮件
 * @ClassName ExpiredBpoSendEmailStrategy
 * @Author wangfenglong
 * @Date 2026/4/22 16:25
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredBpoSendEmailStrategy implements CheckStrategy<UarAlertRequest,ResponseEntity<?>>
{
    private final AlertDashboardService alertDashboardService;
    private final UarMailTemplateService uarMailTemplateService;
    private final AlertDashboardMapper alertDashboardMapper;

    @Override
    public ResponseEntity<?> handle(BatchSendRequest request, UarAlertRequest uarAlertRequest)
    {
        try
        {
            ExpiredBpoBean expiredBpoData = ExpiredBpoDataHandler.getExpiredBpoData(uarAlertRequest, uarMailTemplateService, alertDashboardMapper);
            alertDashboardService.handleExpiredBpoNewFunc(expiredBpoData.getItCode(), expiredBpoData.getDataList(), expiredBpoData.getBpoTemplate(), expiredBpoData.getBatchNo(), expiredBpoData.getSendFlag());
            return ResponseEntity.ok(Map.of( "success", true, "message", "发送提醒邮件成功" ));
        }
        catch (Exception e)
        {
            log.error("处理失效的bpo数据失败：{}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public SendEmailStrategyType getType()
    {
        return SendEmailStrategyType.EXPIRED_BPO_CYCLE_TYPE;
    }

}
