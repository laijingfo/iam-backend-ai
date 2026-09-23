package com.lenovo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.bean.DelegationBean;
import com.lenovo.entity.Delegation;
import com.lenovo.entity.LenovoUser;
import com.lenovo.entity.SendEmailActionLog;
import com.lenovo.entity.UarMailTemplate;
import com.lenovo.mapper.DelegationMapper;
import com.lenovo.notify.NotifyHelper;
import com.lenovo.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


/**
 * @Description TODO 授权表服务接口实现类
 * @author wangfenglong
 * @date 2025/11/18 17:16
**/
@Slf4j
@Service
@RequiredArgsConstructor
public class DelegationServiceImpl extends ServiceImpl<DelegationMapper, Delegation> implements DelegationService
{
    private final UarMailTemplateService uarMailTemplateService;
    private final UarMailService uarMailService;
    private final NotifyHelper notifyHelper;
    private final SendEmailActionLogService sendEmailActionLogService;
    private final LenovoService lenovoService;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");


    /**
     * @Description TODO IT权限查全部数据
     * @author wangfenglong
     * @date 2026/1/29 14:56
    **/
    @Override
    public Page<Delegation> query(DelegationBean delegationBean, Integer page, Integer size)
    {
        Page p = new Page(page, size);
        return getBaseMapper().query(p, delegationBean);
    }

    /**
     * @Description TODO 授权信息写入到delegation表
     * @author wangfenglong
     * @date 2026/1/29 13:13
    **/
    @Override
    public Integer insertDelegation(DelegationBean delegationBean)
    {
        List<Delegation> delegationList = getBaseMapper().getDelegationByReapeat(delegationBean);
        if(!CollectionUtils.isEmpty(delegationList)) {return 3;}
        return getBaseMapper().insertDelegation(delegationBean);
    }

    /**
     * @Description TODO 逻辑删除，1:已删除,0:未删除
     * @author wangfenglong
     * @date 2025/11/21 16:18
    **/
    @Override
    public Integer deleteDelegationByUpdateId(Delegation delegation)
    {
        return getBaseMapper().updateById(delegation);
    }

    /**
     * @Description TODO 授权BPO，选择出需要授权的BPO数据
     * @author wangfenglong
     * @date 2026/1/28 11:10
    **/
    @Override
    public List<DelegationBean> getCurrentItCodeInUserAccessReviewBpoData(DelegationBean delegationBean)
    {
        return getBaseMapper().getBpsByUserAccessReview(delegationBean);
    }

    /**
     * @Description TODO 给被授权人发邮件
     * @author wangfenglong
     * @date 2025/12/17 17:15
    **/
    @Async
    @Override
    public void sendDelegateeEmail(DelegationBean delegationBean)
    {
        String tag = "1";
        String toSomeone = "ToDelegatee";
        String recipientEmail = delegationBean.getDelegatee() + "@lenovo.com";
        String ccEmail = delegationBean.getDelegator() + "@lenovo.com";
        String batchNo = String.format("%s_%s_%s_%s", toSomeone, delegationBean.getDelegatee(), LocalDateTime.now().format(formatter), UUID.randomUUID().toString().replace("-", ""));
        UarMailTemplate template = uarMailTemplateService.findTemplateWithMaxVersion(tag, toSomeone);
        if (Objects.isNull(template))
        {
            log.error("给被授权人发邮件:邮件发送失败: 未找到对应模板");
            SendEmailActionLog sendEmailActionLog = new SendEmailActionLog();
            sendEmailActionLog.setItCode(delegationBean.getDelegatee());
            sendEmailActionLog.setOperation("我的委托-给委托人发送邮件失败");
            sendEmailActionLog.setSendFlag(toSomeone);
            sendEmailActionLog.setMessage("模版是null:tag=" + tag + ", toSomeone=" + toSomeone);
            sendEmailActionLog.setStackTrace("模版是null");
            sendEmailActionLog.setBatchNo(batchNo);
            sendEmailActionLog.setCreateDate(LocalDateTime.now());
            sendEmailActionLogService.addSendEmailLog(sendEmailActionLog);
            return;
        }
        Map<String, String> variables = getVariables(delegationBean);

        // 根据委托人员ITCode获取邮箱，当无实际邮箱后使用字符串拼接邮箱
        LenovoUser recipientUserInfo = lenovoService.getUserInfoByItCode(delegationBean.getDelegatee());
        if ( recipientUserInfo != null && recipientUserInfo.getEmail() != null && !recipientUserInfo.getEmail().isEmpty() ) {
            recipientEmail = recipientUserInfo.getEmail();
        }
        // 根据授权人员ITCode获取邮箱，当无实际邮箱后使用字符串拼接邮箱
        LenovoUser ccUserInfo = lenovoService.getUserInfoByItCode(delegationBean.getDelegator());
        if ( ccUserInfo != null && ccUserInfo.getEmail() != null && !ccUserInfo.getEmail().isEmpty() ){
            ccEmail = ccUserInfo.getEmail();
        }

        try
        {
            uarMailService.sendTemplatedEmailOnlyForLmBpoAndUARSetting(template, Collections.singleton(recipientEmail), Collections.singleton(ccEmail), variables, batchNo,toSomeone);
            log.info("给被授权人发邮件:邮件发送成功: toSomeone={}, tag={}, recipientEmail={}", toSomeone, tag, recipientEmail);
        }
        catch (Exception e)
        {
            log.error("给被授权人发邮件:邮件发送失败: toSomeone={}, tag={}, recipientEmail={},errorMsg={}", toSomeone, tag, recipientEmail, e.getMessage());
            SendEmailActionLog sendEmailActionLog = new SendEmailActionLog();
            sendEmailActionLog.setItCode(delegationBean.getDelegatee());
            sendEmailActionLog.setOperation("我的委托-给委托人发送邮件失败:recipientEmail=" + recipientEmail);
            sendEmailActionLog.setSendFlag(toSomeone);
            sendEmailActionLog.setMessage(e.getMessage());
            sendEmailActionLog.setStackTrace(Arrays.toString(e.getStackTrace()));
            sendEmailActionLog.setBatchNo(batchNo);
            sendEmailActionLog.setCreateDate(LocalDateTime.now());
            sendEmailActionLogService.addSendEmailLog(sendEmailActionLog);
        }
    }

    private Map<String, String> getVariables(DelegationBean delegationBean)
    {
        Map<String, String> variables = new HashMap<>();
        variables.put("{delegatee}", delegationBean.getDelegatee());
        variables.put("{delegator}", delegationBean.getDelegator());
        variables.put("{delegationStartDate}", delegationBean.getDelegationStartDate().toString());
        variables.put("{delegationEndDate}", delegationBean.getDelegationEndDate().toString());
        variables.put("{reasonForDelegation}", delegationBean.getReasonForDelegation());
        variables.put("{delegationCmdbId}", delegationBean.getDelegationCmdbId());
        String scopeDesc;
        String scopeDescEn;
        switch (delegationBean.getDelegationScope())
        {
            case "1":
                scopeDesc = "直属经理权限";
                scopeDescEn = "Line Manager Access Rights";
                break;
            case "2":
                if("ALL".equals(delegationBean.getDelegationCmdbId()))
                {
                    scopeDesc = "BPO全部权限";
                    scopeDescEn = "BPO Access Rights";
                    break;
                }
                scopeDesc = "BPO权限:"+delegationBean.getDelegationCmdbId();
                scopeDescEn = "BPO Access Rights:"+delegationBean.getDelegationCmdbId();
                break;
            case "3":
                scopeDesc = "直属经理和BPO权限";
                scopeDescEn = "Line Manager Access Rights and BPO Access Rights";
                break;
            default:
                scopeDesc = "未知";
                scopeDescEn = "UFO";
                break;
        }
        variables.put("{delegationScope}", scopeDesc);
        variables.put("{delegationScopeEn}", scopeDescEn);
        return variables;
    }


    /**
     * @Description TODO 判断当前登陆账户是否被授权和被授权的cmdbId
     * @author wangfenglong
     * @date 2026/1/29 16:10
    **/
    @Override
    public Map<String, String> getDelegationCmdbIdList(String itCode)
    {
        DelegationBean delegationBean = new DelegationBean();
        delegationBean.setDelegatee(itCode);
        delegationBean.setDelegationScope("BPO");
        Map<String, String> delegatorCmdbIdMap = new HashMap<>();
        List<Delegation> delegationsList = getBaseMapper().getDelegationByDataAndBps(delegationBean);
        if(CollectionUtils.isEmpty(delegationsList))
        {
            return null;
        }
        for(Delegation  delegation : delegationsList)
        {
            if(delegation.getDelegationCmdbId().contains("ALL"))
            {
                delegatorCmdbIdMap.put(delegation.getDelegator(), "ALL");
            }
            else
            {
                delegatorCmdbIdMap.put(delegation.getDelegator(), delegation.getDelegationCmdbId());
            }
        }
        return delegatorCmdbIdMap;
    }
}
