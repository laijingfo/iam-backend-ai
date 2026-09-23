package com.lenovo.strategy.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.lenovo.bean.PreSendBean;
import com.lenovo.dto.BatchSendRequest;
import com.lenovo.entity.AllowSendEmailForBpo;
import com.lenovo.entity.SendEmailActionLog;
import com.lenovo.mapper.UserAccessReviewMapper;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.service.PreSendMailService;
import com.lenovo.service.SendEmailActionLogService;
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
 * @Description TODO UAR发送Bpo通知邮件
 * @ClassName UarSendBpoStrategy
 * @Author wangfenglong
 * @Date 2026/6/3 17:31
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class UarSendBpoStrategy implements CheckStrategy<String, ResponseEntity<?>>
{
    private final ISendLmOrBpoEmailService sendLmOrBpoEmailService;
    private final SendEmailActionLogService sendEmailActionLogService;
    private final UserAccessReviewMapper userAccessReviewMapper;
    private final PreSendMailService preSendMailService;


    @Override
    public ResponseEntity<?> handle(BatchSendRequest request, String param)
    {
        String sendFlag = "UARSendBpoNotifEmail";
        String itCode = SecurityUtils.getCurrentUserId();
        String taskId = String.format("%s_%s_%s", sendFlag, itCode, UUID.randomUUID().toString().replace("-", ""));
        log.info("{}-发送UAR邮件-给Bpo发送邮件-发送邮件：任务ID：{}，开始提交异步任务", sendFlag, taskId);
        List<PreSendBean> preSendBpoList = preSendMailService.getPreSendBpoList();

        sendLmOrBpoEmailService.batchSendBpoEmailsAsync(preSendBpoList, itCode, sendFlag, String.valueOf(request.getRound()));


        return ResponseEntity.ok(Map.of("success", true, "message", I18nUtil.get("send.email.msg2")));
    }

    @Override
    public SendEmailStrategyType getType()
    {
        return SendEmailStrategyType.UAR_BPO_SEND_TYPE;
    }
}
