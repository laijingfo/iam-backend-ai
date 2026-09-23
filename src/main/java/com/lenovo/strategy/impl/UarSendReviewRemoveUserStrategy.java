package com.lenovo.strategy.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.lenovo.async.SendMailTask;
import com.lenovo.bean.PreSendBean;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.dto.BatchSendRequest;
import com.lenovo.entity.SendEmailActionLog;
import com.lenovo.mapper.UserAccessReviewMapper;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.service.PreSendMailService;
import com.lenovo.service.SendEmailActionLogService;
import com.lenovo.service.impl.IEmailSendService;
import com.lenovo.strategy.CheckStrategy;
import com.lenovo.strategy.SendEmailStrategyType;
import com.lenovo.util.I18nUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @Description TODO 给lm或者bpo审核是remove的用户发送邮件 <还是觉得你最好,张学友>
 * @ClassName UarSendReviewRemoveUserStrategy
 * @Author wangfenglong
 * @Date 2026/6/25 17:45
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class UarSendReviewRemoveUserStrategy implements CheckStrategy<Void, ResponseEntity<?>>
{
    private final IEmailSendService emailSendService;
    private final UserAccessReviewMapper userAccessReviewMapper;
    private final SendEmailActionLogService sendEmailActionLogService;
    private final PreSendMailService preSendMailService;

    @Override
    public ResponseEntity<?> handle(BatchSendRequest request, Void param)
    {
        String sendFlag = "SendReviewRemoveUserEmail";
        String itCode = SecurityUtils.getCurrentUserId();
        UseAccessReviewBean useAccessReviewBean = new UseAccessReviewBean();
        List<PreSendBean> resultList = preSendMailService.getPreSendUserWillRemoveList();
        if(CollectionUtil.isEmpty(resultList))
        {
            return ResponseEntity.ok(Map.of("success", false, "message", "allow_send_email_for_result_remove_user" + I18nUtil.get("send.email.error1")));
        }

        String taskId = String.format("%s_%s_%s",sendFlag,itCode, UUID.randomUUID().toString().replace("-", ""));
        log.info("{}-待发送页面-给lm或者bpo审核是remove的用户发送邮件：任务ID：{}，开始提交异步任务", sendFlag,taskId);
        emailSendService.batchSendReviewRemoveUserEmailsAsync(resultList,itCode,sendFlag);

        return ResponseEntity.ok(Map.of("success", true, "message", "[" + resultList.size() + "]" + I18nUtil.get("send.email.msg2")));
    }

    @Override
    public SendEmailStrategyType getType()
    {
        return SendEmailStrategyType.UAR_REVIEW_REMOVE_USER_SEND_TYPE;
    }
}
