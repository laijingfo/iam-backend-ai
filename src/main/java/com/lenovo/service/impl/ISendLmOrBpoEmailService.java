package com.lenovo.service.impl;

import com.lenovo.bean.PreSendBean;
import com.lenovo.dto.BatchSendRequest;
import com.lenovo.dto.BatchSendResult;
import com.lenovo.entity.AllowSendEmailForBpo;
import com.lenovo.entity.AllowSendEmailForLm;
import com.lenovo.entity.UserAccessReview;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author wangFenglong
 * @data 2026/6/3
 **/
public interface ISendLmOrBpoEmailService
{
    /**
     * @Description TODO 新需求：给lm或者bpo发送邮件
     * @param [request, allowSendEmaiList, itCode, sendFlag]
     * @author wangfenglong
     * @date 2026/6/3 15:02
    **/
    CompletableFuture<List<BatchSendResult>> batchSendLmEmailsAsync(List<PreSendBean> preSendBpoList, String itCode, String sendFlag,String param);
    CompletableFuture<List<BatchSendResult>> batchSendBpoEmailsAsync(List<PreSendBean> preSendBpoList, String itCode, String sendFlag, String param);

    List<UserAccessReview> getLmSelectedRecords(BatchSendRequest request, String successFailedFlag);
    List<UserAccessReview> getBpoSelectedRecords(BatchSendRequest request, String successFailedFlag);
}
