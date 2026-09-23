package com.lenovo.strategy.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.lenovo.bean.PreSendBean;
import com.lenovo.dto.BatchSendRequest;
import com.lenovo.entity.AllowSendEmailForLm;
import com.lenovo.entity.SendEmailActionLog;
import com.lenovo.entity.UserAccessReview;
import com.lenovo.mapper.AllowSendEmailForLmFailedMapper;
import com.lenovo.mapper.UserAccessReviewMapper;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.service.PreSendMailService;
import com.lenovo.service.SendEmailActionLogService;
import com.lenovo.service.UserAccessReviewService;
import com.lenovo.service.impl.IEmailSendService;
import com.lenovo.service.impl.ISendLmOrBpoEmailService;
import com.lenovo.strategy.CheckStrategy;
import com.lenovo.strategy.SendEmailStrategyType;
import com.lenovo.util.I18nUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * @Description TODO UAR发送Lm通知邮件
 * @ClassName UarSendLmStrategy
 * @Author wangfenglong
 * @Date 2026/6/2 14:12
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class UarSendLmStrategy implements CheckStrategy<String, ResponseEntity<?>>
{
    private final ISendLmOrBpoEmailService sendLmOrBpoEmailService;
    private final SendEmailActionLogService sendEmailActionLogService;
    private final UserAccessReviewMapper userAccessReviewMapper;
    private final AllowSendEmailForLmFailedMapper allowSendEmailForLmFailedMapper;
    private final PreSendMailService preSendMailService;

    @Override
    public ResponseEntity<?> handle(BatchSendRequest request, String param)
    {
        String sendFlag = "UARSendLmNotifEmail";
        String itCode = SecurityUtils.getCurrentUserId();
        String taskId = String.format("%s_%s_%s",sendFlag,itCode, UUID.randomUUID().toString().replace("-", ""));
        log.info("{}-发送UAR邮件-给lineManager发送邮件-发送邮件：任务ID：{}，开始提交异步任务", sendFlag,taskId);

        List<PreSendBean> preSendLmList = preSendMailService.getPreSendLmList();
        // 新
        sendLmOrBpoEmailService.batchSendLmEmailsAsync(preSendLmList, itCode, sendFlag, String.valueOf(request.getRound()));
        // 旧
//        sendLmOrBpoEmailService.batchSendLmOrBpoEmailsAsync(request,allowSendEmailList,itCode,sendFlag,param);


        return ResponseEntity.ok(Map.of("success", true, "message", I18nUtil.get("send.email.msg2")));

    }

    @Override
    public SendEmailStrategyType getType()
    {
        return SendEmailStrategyType.UAR_LM_SEND_TYPE;
    }
}
