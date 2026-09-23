package com.lenovo.service.impl;

import com.lenovo.bean.PreSendBean;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.dto.BatchSendRequest;
import com.lenovo.dto.BatchSendResult;
import com.lenovo.entity.AllowSendEmailIncludeFinalRemoveUser;
import com.lenovo.entity.AutoMailSendBpoTemp;
import com.lenovo.entity.AutoMailSendLineManagerTemp;
import com.lenovo.entity.UserAccessReview;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * @author wangFenglong
 * @data 2025/12/13
 **/
public interface IEmailSendService
{
    //发送正常的邮件，获取需要发送的邮件集合
    List<UserAccessReview> getAllowSendEmaiList(List<UserAccessReview> selectedRecords) throws Exception;

    //发送邮件校验前把需要发送的邮件都处理好写入到数据给领导看
    void getAllowSendEmailForLead(List<UserAccessReview> selectedRecords) throws Exception;


    void saveBpoAndLmInfoToDb(String flag,List<UserAccessReview> selectedRecords,String successFailed,BatchSendRequest request) throws Exception;



    //给所有lm和bpo发邮件
    void sendAllEmail(List<AutoMailSendLineManagerTemp> linaManagerSendDataList, List<AutoMailSendBpoTemp> bpoSendDataList, String batchNo, String itCode, String nodeId);

    //后台定时任务发送邮件_获取需要发送的邮件的数据
    List<AutoMailSendLineManagerTemp> getLinaManagerSendData();

    //发送邮件
    <T> boolean sendMail(T mail,String toSomeone,String finalBatchNo,String itCode);

    //查询ad_tb_upp_nature_2用户表获取高管(不能给高管发底层牛马人的邮件)
    List<String> getUserBySVPBandFlag(String flag);

    //生成批次号
    String generateBatchNoByScheduledTask();


    //用户发邮件--查询final_review_decision最终审核是空的用户数据
    List<String>  getUserByFinalReviewDecisionIsEmpty(UseAccessReviewBean useAccessReviewBean, String itCode, String sendFlag,String roleFlag);

    //用户发邮件--查询lm或者bpo的审核结果是remove的用户数据
    List<String>  getUserByReviewResultIsRemove(UseAccessReviewBean useAccessReviewBean, String itCode, String sendFlag,String roleFlag);

    //用户发邮件--写入到allow_send_email_for_result_remove_user表
    void saveUserToAllowSendEmailForResultRemoveUser(List<String> newUserList,String itCode,String sendFlag);

    //用户发邮件--写入到allow_send_email_include_user表
    void saveUserToAllowSendEmailIncludeUser(List<String> newUserList,String itCode,String sendFlag);
    //用户发邮件--从数据库查询出已经准备好的用户邮件信息
    List<String>  getAllowSendEmailIncludeUserData(UseAccessReviewBean useAccessReviewBean);
    //用户发邮件--发送邮件
    CompletableFuture<List<BatchSendResult>> batchSendUserEmailsAsync(List<PreSendBean> resultList, String itCode, String sendFlag);

    //用户发邮件--发送最终审核是移除的用户邮件
    CompletableFuture<List<BatchSendResult>> batchSendFinalRemoveUserEmailsAsync(Map<String, byte[]> excelMap, List<PreSendBean> resultList, String itCode, String sendFlag);

    //用户发邮件--获取最终审核是移除的用户数据
    List<UserAccessReview> getFinalRemoveUser(UseAccessReviewBean useAccessReviewBean, String itCode, String sendFlag,String roleFlag);
    //用户发邮件--将最终审核是移除的用户写入到allow_send_email_include_final_remove_user表
    void saveFinalRemoveUserToAllowSendEmailIncludeFinalRemoveUserData(List<UserAccessReview> newUserList,String itCode,String sendFlag);

    //给lm或者bpo审核是remove的用户发送邮件
    CompletableFuture<List<BatchSendResult>> batchSendReviewRemoveUserEmailsAsync(List<PreSendBean> resultList, String itCode, String sendFlag);



    //原子化抢占LM邮件并标记为发送中（同一个事务）
    List<AutoMailSendLineManagerTemp> preemptAndMarkLmMails(int batchSize, String nodeId);

    //原子化抢占BPO邮件并标记为发送中（同一个事务）
    List<AutoMailSendBpoTemp> preemptAndMarkBpoMails(int batchSize, String nodeId);

    //等待所有异步任务完成
    void waitForAllTasksComplete(List<Future<?>> futureList, String batchNo, String nodeId);

    //异步发送BPO邮件（修复接收人类型硬编码错误）
    void sendBpoMailsAsync(List<AutoMailSendBpoTemp> bpoMails, String batchNo, String itCode, String nodeId, List<Future<?>> futureList);

    //异步发送LineManager邮件
    void sendLmMailsAsync(List<AutoMailSendLineManagerTemp> lmMails, String batchNo, String itCode, String nodeId, List<Future<?>> futureList);

    //重置超时邮件（兜底逻辑）
    void resetTimeoutMails(String nodeId, String batchNo);
}
