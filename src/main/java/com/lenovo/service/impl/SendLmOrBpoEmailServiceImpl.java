package com.lenovo.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.lenovo.async.SendMailTask;
import com.lenovo.bean.EmailFailedBean;
import com.lenovo.bean.PreSendBean;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.config.GlobalBusinessStatusEnum;
import com.lenovo.dto.BatchSendRequest;
import com.lenovo.dto.BatchSendResult;
import com.lenovo.entity.*;
import com.lenovo.mapper.ItsApplicationDataMapper;
import com.lenovo.mapper.UserAccessReviewMapper;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.security.utils.StringUtils;
import com.lenovo.service.*;
import com.lenovo.util.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @Description TODO 用心灵温暖黑夜孤独的泪光, 让青春烈火燃烧永恒,让生命闪电划过天边,向浩瀚星空许下诺言,让年轻的心永不改变.
 * @ClassName SendLmOrBpoEmailServiceImpl
 * @Author wangfenglong
 * @Date 2026/6/3 15:05
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class SendLmOrBpoEmailServiceImpl implements ISendLmOrBpoEmailService
{
    private final UarMailTemplateService uarMailTemplateService;
    private final UarMailService uarMailService;
    private final RedisUtils redisUtils;
    private final UseAccessReviewEmailSendService emailSendService;
    private final UserAccessReviewMapper userAccessReviewMapper;
    private final SendEmailActionLogService sendEmailActionLogService;
    private final SendMailTask sendMailTask;

    @Resource(name = "sendLineManagerMailExecutor")
    private ThreadPoolTaskExecutor sendLineManagerMailExecutor;
    @Resource(name = "sendBpoMailExecutor")
    private ThreadPoolTaskExecutor sendBpoMailExecutor;


    /**
     * @Description TODO 发送Lm邮件（第一入口）
     * @author wangfenglong
     * @date 2026/6/3 17:49
    **/
    @Override
    public CompletableFuture<List<BatchSendResult>> batchSendLmEmailsAsync(List<PreSendBean> preSendBpoList, String itCode, String sendFlag,String round)
    {
        //线程安全的结果集合
        List<BatchSendResult> results = Collections.synchronizedList(new ArrayList<>());

        String batchNo = EmailSendServiceAsync.generateBatchNo(itCode,sendFlag);
        String lm = "ToLineManager";
        UarMailTemplate lMTemplate = uarMailTemplateService.findTemplateWithMaxVersion(round, lm);
        if(lMTemplate == null)
        {
            String errorMsg = sendFlag + ",No corresponding template was found.未找到对应模板: tag=" + round + ", toSomeone=ToLineManager";
            log.error(errorMsg);
            return CompletableFuture.failedFuture(new RuntimeException(errorMsg));
        }

        try
        {
            for (PreSendBean record :preSendBpoList)
            {
                sendMailTask.sendEmailToRecipientLm(record, batchNo, lMTemplate, sendFlag);
            }

            return CompletableFuture.completedFuture(results);
        }
        catch (Exception e)
        {
            log.error("{}-批量发送邮件失败【批次号：{}】，异步任务执行过程中抛出异常:", sendFlag,batchNo, e);
            log.error("{}-The batch email sending failed [Batch ID: {}], and an exception occurred during the execution of the asynchronous task:", sendFlag,batchNo, e);
            SendEmailActionLog sendEmailActionLog = new SendEmailActionLog();
            sendEmailActionLog.setItCode(itCode);
            sendEmailActionLog.setOperation(sendFlag + "-UAR管理:UAR发送邮件：第一入口出现异常:");
            sendEmailActionLog.setSendFlag(sendFlag);
            sendEmailActionLog.setMessage(e.getMessage());
            sendEmailActionLog.setStackTrace(Arrays.toString(e.getStackTrace()));
            sendEmailActionLog.setBatchNo(batchNo);
            sendEmailActionLog.setCreateDate(LocalDateTime.now());
//            sendEmailActionLogService.addSendEmailLog(sendEmailActionLog);
            return CompletableFuture.failedFuture(e);
        }

    }

    /**
     * @Description TODO 发送Bpo邮件（第一入口）
     * @param [request, allowSendEmaiList, itCode, sendFlag]
     * @author wangfenglong
     * @date 2026/6/3 17:51
    **/
    @Override
    public CompletableFuture<List<BatchSendResult>> batchSendBpoEmailsAsync(List<PreSendBean> preSendBpoList, String itCode, String sendFlag, String round)
    {
        //线程安全的结果集合
        List<BatchSendResult> results = Collections.synchronizedList(new ArrayList<>());

        // 批次号
        String batchNo = EmailSendServiceAsync.generateBatchNo(itCode,sendFlag);
        int total = preSendBpoList.size();

        // 获取邮件模板
        String bpo ="ToBPO";
        UarMailTemplate bpoTemplate = uarMailTemplateService.findTemplateWithMaxVersion(round, bpo);
        if(bpoTemplate == null)
        {
            String errorMsg = sendFlag + ",No corresponding template was found.未找到对应模板: tag=" + round + ", toSomeone=ToBPO";
            log.error(errorMsg);
            return CompletableFuture.failedFuture(new RuntimeException(errorMsg));
        }


        try
        {
            for (PreSendBean record :preSendBpoList)
            {
                sendMailTask.sendEmailToRecipientBpo(record, batchNo, bpoTemplate, sendFlag);
            }
        }
        catch (Exception e)
        {
            log.error("{}-批量发送邮件失败【批次号：{}】，异步任务执行过程中抛出异常:", sendFlag,batchNo, e);
            log.error("{}-The batch email sending failed [Batch ID: {}], and an exception occurred during the execution of the asynchronous task:", sendFlag,batchNo, e);
            SendEmailActionLog sendEmailActionLog = new SendEmailActionLog();
            sendEmailActionLog.setItCode(itCode);
            sendEmailActionLog.setOperation(sendFlag + "-UAR管理:UAR发送邮件：第一入口出现异常:");
            sendEmailActionLog.setSendFlag(sendFlag);
            sendEmailActionLog.setMessage(e.getMessage());
            sendEmailActionLog.setStackTrace(Arrays.toString(e.getStackTrace()));
            sendEmailActionLog.setBatchNo(batchNo);
            sendEmailActionLog.setCreateDate(LocalDateTime.now());
            sendEmailActionLogService.addSendEmailLog(sendEmailActionLog);
            return CompletableFuture.failedFuture(e);
        }
        return CompletableFuture.completedFuture(results);
    }


    private void recordEmailSendStatus(String uuid, Integer round, String recipientType, String recipientEmail, Long templateId, String status, String errorMsg, String batchNo)
    {
        UseAccessReviewEmailSend sendRecord = new UseAccessReviewEmailSend();
        sendRecord.setReviewUuid(uuid);
        sendRecord.setSendRound(round);
        sendRecord.setSendTime(LocalDateTime.now());
        sendRecord.setSendStatus(status);
        sendRecord.setRecipientType(recipientType);
        sendRecord.setRecipientEmail(recipientEmail);
        sendRecord.setTemplateId(templateId);
        sendRecord.setErrorMessage(errorMsg);
        sendRecord.setBatchNo(batchNo);
        emailSendService.save(sendRecord);
    }


    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
    @Override
    public List<UserAccessReview> getLmSelectedRecords(BatchSendRequest request,String successFailedFlag)
    {
        List<UserAccessReview> selectedRecords;
        EmailFailedBean roleBean = this.getCurrentUserRole();
        if (request.getIsALL())
        {
            UseAccessReviewBean bean = this.getCurrentRequestFilters(request);
            if("3".equals(roleBean.getRoleFlag()))
            {
                bean.setCmdbIdList(roleBean.getCmdbIdList());
            }
            selectedRecords = userAccessReviewMapper.getLmFromUserAccessReviewNew(bean,roleBean.getRoleFlag(),successFailedFlag);
        }
        else
        {
            if(request.getSequenceNumbers() == null || request.getSequenceNumbers().isEmpty())
            {
                return List.of();
            }
            selectedRecords = userAccessReviewMapper.getAllNeeDToSendEmailFromUserAccessReviewBySequenceNumbers(request.getSequenceNumbers());
        }
        if(CollectionUtil.isEmpty(selectedRecords))
        {
            return List.of();
        }
        for(UserAccessReview userAccessReviewTemp : selectedRecords)
        {
            if(null == userAccessReviewTemp.getLineManagerEmail() || userAccessReviewTemp.getLineManagerEmail().trim().isEmpty() || "null".equalsIgnoreCase(userAccessReviewTemp.getLineManagerEmail()))
            {
                //userAccessReviewTemp.setLineManagerEmail(userAccessReviewTemp.getLineManager() + "@lenovo.com");
                if(StringUtils.isNotBlank(userAccessReviewTemp.getLineManager()))
                {
                    List<String> userEmailList = userAccessReviewMapper.getUserEmailByUserName(userAccessReviewTemp.getLineManager().toLowerCase());
                    if(!userEmailList.isEmpty() && StringUtils.isNotBlank(userEmailList.get(0)))
                    {
                        userAccessReviewTemp.setLineManagerEmail(userEmailList.get(0));
                    }
                }
            }
        }
        return selectedRecords;
    }

    @Override
    public List<UserAccessReview> getBpoSelectedRecords(BatchSendRequest request,String successFailedFlag)
    {
        List<UserAccessReview> selectedRecords;
        EmailFailedBean roleBean = this.getCurrentUserRole();
        if (request.getIsALL())
        {
            UseAccessReviewBean bean = this.getCurrentRequestFilters(request);
            if("3".equals(roleBean.getRoleFlag()))
            {
                bean.setCmdbIdList(roleBean.getCmdbIdList());
            }
            selectedRecords = userAccessReviewMapper.getBpoFromUserAccessReviewNew(bean,roleBean.getRoleFlag(),successFailedFlag);

        }
        else
        {
            if(request.getSequenceNumbers() == null || request.getSequenceNumbers().isEmpty())
            {
                return List.of();
            }
            selectedRecords = userAccessReviewMapper.getAllNeeDToSendEmailFromUserAccessReviewBySequenceNumbers(request.getSequenceNumbers());

        }
        if(CollectionUtil.isEmpty(selectedRecords))
        {
            return List.of();
        }
        for(UserAccessReview userAccessReviewTemp : selectedRecords)
        {
            if(null == userAccessReviewTemp.getBpoEmail() || userAccessReviewTemp.getBpoEmail().trim().isEmpty() || "null".equalsIgnoreCase(userAccessReviewTemp.getBpoEmail()))
            {
                //userAccessReviewTemp.setBpoEmail(userAccessReviewTemp.getBpo() + "@lenovo.com");
                if(StringUtils.isNotBlank(userAccessReviewTemp.getBpo()))
                {
                    List<String> userEmailList = userAccessReviewMapper.getUserEmailByUserName(userAccessReviewTemp.getBpo().toLowerCase());
                    if(!userEmailList.isEmpty() && StringUtils.isNotBlank(userEmailList.get(0)))
                    {
                        userAccessReviewTemp.setBpoEmail(userEmailList.get(0));
                    }
                }
            }
        }
        return selectedRecords;
    }

    public EmailFailedBean getCurrentUserRole()
    {
        EmailFailedBean bean = new EmailFailedBean();
        String itCode = SecurityUtils.getCurrentUserId();
        Role roleContainList = (Role)redisUtils.hget(itCode+ GlobalBusinessStatusEnum.REDIS_KEY_USER_ROLE.desc,itCode);
        if(Objects.isNull(roleContainList) || CollectionUtil.isEmpty(roleContainList.getRoleList()))
        {
            bean.setRoleFlag("1");
            return bean;
        }

        List<String> SysITRoleList = List.of("View_Only_IT","UAR_Admin_IT","UAR_System_Admin_IT");//IT权限
        List<String> SysUarRoleList = List.of("UAR_Processer");//UAR权限
        List<String> uniqueRoleList = roleContainList.getRoleList().stream().map(Role::getName).distinct().collect(Collectors.toList());

        //包含IT角色
        if(uniqueRoleList.stream().anyMatch(SysITRoleList::contains))
        {
            bean.setRoleFlag("2");
            return bean;
        }

        //包含UAR角色
        if(uniqueRoleList.stream().anyMatch(SysUarRoleList::contains))
        {
            Optional<Role> uarRoleOpt = roleContainList.getRoleList().stream().filter(role -> GlobalBusinessStatusEnum.UAR_PROCESSER.desc.equals(role.getName())).findFirst();//获取name=UAR_Processer的对象
            Role uarRole = uarRoleOpt.orElse(null); // 无匹配时返回null
            if(Objects.nonNull(uarRole))
            {
                //包含UAR_Processer权限，但是没有cmdbID。有权限但是看不到数据
                if(null == uarRole.getCmdbIdOfUarProcesser() || com.lenovo.security.utils.StringUtils.isEmpty(uarRole.getCmdbIdOfUarProcesser()))
                {
                    bean.setRoleFlag("1");
                    return bean;
                }
                List<String> cmdbIdList = Stream.of(uarRole.getCmdbIdOfUarProcesser().split(",")).collect(Collectors.toList());
                bean.setRoleFlag("3");
                bean.setCmdbIdList(cmdbIdList);
                return bean;
            }
            log.error("发送失败页面校验:获取当前登陆账户角uarRoleOpt=null");
            bean.setRoleFlag("1");
            return bean;
        }
        bean.setRoleFlag("1");
        return bean;
    }

    public UseAccessReviewBean getCurrentRequestFilters(BatchSendRequest request)
    {
        Map<String, Object> filterMap = request.getFilters();
        UseAccessReviewBean  bean = new UseAccessReviewBean();

        //应用编号
        Object cmdbId = filterMap.get("cmdbId");
        if(cmdbId != null && !cmdbId.toString().isBlank())
        {
            List<String> cmdbList = Arrays.stream(cmdbId.toString().split(","))
                    .map(String::trim)
                    .filter(trimmedId -> !trimmedId.isEmpty())
                    .collect(Collectors.toList());
            bean.setCmdbIdListOfChoose(cmdbList);
        }

        //应用名称
        Object appName = filterMap.get("appName");
        if(appName != null && !appName.toString().isBlank())
        {
            List<String> appNameList = Arrays.stream(appName.toString().split(","))
                    .map(String::trim)
                    .filter(trimmedId -> !trimmedId.isEmpty())
                    .collect(Collectors.toList());
            bean.setAppNameListOfChoose(appNameList);
        }

        //用户itCode
        Object itCodeOfUser = filterMap.get("itCodeOfUser");
        if(itCodeOfUser != null && !itCodeOfUser.toString().isBlank())
        {
            bean.setItCodeOfUser(itCodeOfUser.toString());
        }

        //直属经理
        Object lineManager = filterMap.get("lineManager");
        if(lineManager != null && !lineManager.toString().isBlank())
        {
            bean.setLineManager(lineManager.toString());
        }

        //直属经理审核状态
        Object lineManagerReviewStatus = filterMap.get("lineManagerReviewStatus");
        if (lineManagerReviewStatus != null && !lineManagerReviewStatus.toString().isBlank())
        {
            bean.setLineManagerReviewStatus(lineManagerReviewStatus.toString());
        }

        //bpo
        Object bpo = filterMap.get("bpo");
        if(bpo != null && !bpo.toString().isBlank())
        {
            bean.setBpo(bpo.toString());
        }

        //bpo审核状态
        Object bpoReviewStatus = filterMap.get("bpoReviewStatus");
        if (bpoReviewStatus != null && !bpoReviewStatus.toString().isBlank())
        {
            bean.setBpoReviewStatus(bpoReviewStatus.toString());
        }

        //直属经理部门
        Object dept = filterMap.get("dept");
        if (dept != null && !dept.toString().isBlank())
        {
            bean.setDept(dept.toString());
        }

        //直属经理职级
        Object lineManagerLevelCode = filterMap.get("lineManagerLevelCode");
        if (lineManagerLevelCode != null && !lineManagerLevelCode.toString().isBlank())
        {
            bean.setLineManagerLevelCode(lineManagerLevelCode.toString());
        }
        return bean;
    }
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//

}
