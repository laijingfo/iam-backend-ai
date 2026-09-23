package com.lenovo.async;

import com.lenovo.bean.PreSendBean;
import com.lenovo.entity.UarMailTemplate;
import com.lenovo.entity.NaSensitiveAccessAlert;
import com.lenovo.entity.LenovoUser;
import com.lenovo.entity.SendEmailActionLog;
import com.lenovo.mapper.NaSensitiveAccessAlertMapper;
import com.lenovo.service.LenovoService;
import com.lenovo.service.SendEmailActionLogService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.lenovo.security.utils.StringUtils;
import com.lenovo.service.PreSendMailService;
import com.lenovo.service.UarMailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 这里放的都是邮件发送任务
 * 需要重构wangfl代码，原先逻辑太乱太啰嗦无法统一梳理，第一次重构(分批业务)全部调整到此处 之后再统一重构新逻辑
 *
 * 发件人统一由底层邮件配置读取，业务任务不再传递发件人参数。
 */

@Component
@RequiredArgsConstructor
@Slf4j
@EnableAsync
public class SendMailTask {

    private final UarMailService uarMailService;
    private final PreSendMailService preSendMailService;
    private final LenovoService lenovoService;
    private final NaSensitiveAccessAlertMapper naSensitiveAccessAlertMapper;
    private final SendEmailActionLogService sendEmailActionLogService;

    /**
     * 发送单条NA敏感权限告警，成功后更新发送时间。
     * 使用现有邮件线程池异步执行。
     */
    @Async("emailSendExecutor")
    public void sendNaSensitiveAccessAlertEmail(NaSensitiveAccessAlert record,
                                               UarMailTemplate template, String sendFlag) {
        String recipientItCode = StringUtils.isNotBlank(record.getOperationFocal())
                ? record.getOperationFocal().trim()
                : StringUtils.trimToEmpty(record.getOperationOwner());
        String batchNo = String.format("%s_%s_%s", sendFlag, record.getId(),
                LocalDateTime.now().format(DateTimeFormatter.BASIC_ISO_DATE));
        if (StringUtils.isBlank(recipientItCode)) {
            SendEmailActionLog actionLog = new SendEmailActionLog();
            actionLog.setItCode("JOB");
            actionLog.setOperation("发送NA-Sensitive权限告警邮件失败");
            actionLog.setSendFlag(sendFlag);
            actionLog.setMessage("Operation Focal和Operation Owner均为空");
            actionLog.setBatchNo(batchNo);
            actionLog.setCreateDate(LocalDateTime.now());
            sendEmailActionLogService.addSendEmailLog(actionLog);
            return;
        }

        // 准备当前告警的模板变量
        Map<String, String> variables = buildNaSensitiveMailVariables(record, recipientItCode);
        try {
            LenovoUser recipient = lenovoService.getUserInfoByItCode(recipientItCode);
            String recipientEmail = recipient == null ? null : recipient.getEmail();
            recipientEmail = StringUtils.isBlank(recipientEmail)
                    ? recipientItCode + "@lenovo.com" : recipientEmail.trim();
            log.info("{}-NA敏感权限告警邮件发送开始：{}", recipientItCode, record.getId());
            uarMailService.sendTemplatedEmailOnlyForLmBpoAndUARSetting(
                    template, Collections.singleton(recipientEmail), null, variables, batchNo, sendFlag);
            naSensitiveAccessAlertMapper.updateLastSendTime(record.getId(), LocalDateTime.now(), "JOB");
        } catch (Exception e) {
            throw new RuntimeException("发送NA敏感权限告警邮件失败: " + e.getMessage(), e);
        }
    }

    private Map<String, String> buildNaSensitiveMailVariables(NaSensitiveAccessAlert alert, String recipientItCode) {
        Map<String, String> variables = new HashMap<>();
        variables.put("{itCode}", recipientItCode);
        variables.put("{appId}", StringUtils.defaultString(alert.getCmdbId()));
        variables.put("{appName}", StringUtils.defaultString(alert.getAppName()));
        variables.put("{itCodeOfUser}", StringUtils.defaultString(alert.getUserItCode()));
        variables.put("{userCocType}", StringUtils.defaultString(alert.getUserCocType()));
        variables.put("{systemRole}", StringUtils.defaultString(alert.getSystemRole()));
        variables.put("{accessLabel}", StringUtils.defaultString(alert.getRoleClassification()));
        variables.put("{alertTriggerTime}", alert.getAlertTriggerTime() == null
                ? "" : alert.getAlertTriggerTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return variables;
    }




    /**
     * 发送邮件任务 To BPO
     * @param record
     * @param batchNo
     * @param template
     */
    @Async("sendBpoMailExecutor")
    public void sendEmailToRecipientBpo(PreSendBean record, String batchNo, UarMailTemplate template, String sendFlag)
    {
        //准备模板变量
        Map<String, String> variables = new HashMap<>();
        variables.put("{itCode}", record.getTo());

        try
        {
            String threadName = Thread.currentThread().getName();
            log.info("{}-邮件发送开始：{}", record.getTo(),threadName);
            uarMailService.sendTemplatedEmailOnlyForLmBpoAndUARSetting(template, Collections.singleton(record.getToEmail()), null, variables, batchNo, sendFlag);
            preSendMailService.removePreSendBpoById(record.getId());
        }
        catch (Exception e)
        {
            throw new RuntimeException("待发送页面-发送邮件开始：邮件发送失败: " + e.getMessage() , e);
        }
    }


    /**
     * 邮件发送任务 To LM
     * @param record
     * @param batchNo
     * @param template
     */
    @Async("sendLineManagerMailExecutor")
    public void sendEmailToRecipientLm(PreSendBean record, String batchNo, UarMailTemplate template, String sendFlag)
    {
        //准备模板变量
        Map<String, String> variables = new HashMap<>();
        variables.put("{itCode}", record.getTo());

        try
        {
            String threadName = Thread.currentThread().getName();
            log.info("{}-邮件发送开始：{}", record.getTo(),threadName);
            Set<String> ccEmailList = parseEmailAddresses(record.getCcEmail());

            uarMailService.sendTemplatedEmailOnlyForLmBpoAndUARSetting(template, Collections.singleton(record.getToEmail()), ccEmailList, variables, batchNo, sendFlag);
            preSendMailService.removePreSendLmById(record.getId());
        }
        catch (Exception e)
        {
            throw new RuntimeException("待发送页面-发送邮件开始：邮件发送失败: " + e.getMessage() , e);
        }
    }

    /**
     * 邮件发送任务 To User  [Remove] 移除
     * @param record
     * @param batchNo
     * @param template
     */
    @Async("sendUserEmailExecutor")
    public void sendRemoveRoleEmailToRecipientUser(PreSendBean record, String batchNo, UarMailTemplate template, String sendFlag)
    {
        //准备模板变量
        Map<String, String> variables = new HashMap<>();
        variables.put("{itCode}", record.getTo());

        try
        {
            String threadName = Thread.currentThread().getName();
            log.info("{}-邮件发送开始：{}", record.getTo(),threadName);

            uarMailService.sendTemplatedEmailOnlyForLmBpoAndUARSetting(template, Collections.singleton(record.getToEmail()), null, variables, batchNo, sendFlag);
            preSendMailService.removePreSendUserWillRemoveById(record.getId());
        }
        catch (Exception e)
        {
            throw new RuntimeException("待发送页面-发送邮件开始：邮件发送失败: " + e.getMessage() , e);
        }
    }

    /**
     * 邮件发送任务 To User  [Reminder] 催办
     * @param record
     * @param batchNo
     * @param template
     */
    @Async("sendUserEmailExecutor")
    public void sendReminderEmailToRecipientUser(PreSendBean record, String batchNo, UarMailTemplate template, String sendFlag)
    {
        //准备模板变量
        Map<String, String> variables = new HashMap<>();
        variables.put("{itCode}", record.getTo());

        try
        {
            String threadName = Thread.currentThread().getName();
            log.info("{}-邮件发送开始：{}", record.getTo(),threadName);

            uarMailService.sendTemplatedEmailOnlyForLmBpoAndUARSetting(template, Collections.singleton(record.getToEmail()), null, variables, batchNo, sendFlag);
            preSendMailService.removePreSendReminderUserById(record.getId());
        }
        catch (Exception e)
        {
            throw new RuntimeException("待发送页面-发送邮件开始：邮件发送失败: " + e.getMessage() , e);
        }
    }

    /**
     * 给最终审核结果为移除的用户发送带附件邮件。
     * 发送成功后删除对应预发送记录，失败时保留记录供后续重试。
     */
    @Async("sendUserEmailExecutor")
    public void sendFinalRemoveEmailToRecipientUser(PreSendBean record, byte[] excelBytes,
                                                     String batchNo, UarMailTemplate template, String sendFlag)
    {
        Map<String, String> variables = new HashMap<>();
        variables.put("{itCode}", record.getTo());
        variables.put("{dueDate}", Objects.isNull(template.getDueDate())
                ? "dateTime"
                : new SimpleDateFormat("yyyy-MM-dd").format(template.getDueDate()));
        try
        {
            uarMailService.sendTemplatedEmailOnlyFinalRemoveUserByExcel(
                    excelBytes,
                    record.getTo(),
                    template,
                    Collections.singleton(record.getToEmail()),
                    null,
                    variables,
                    batchNo,
                    sendFlag
            );
            preSendMailService.removePreSendFinalRemoveUserById(record.getId());
        }
        catch (Exception e)
        {
            throw new RuntimeException("发送最终移除用户邮件失败: " + e.getMessage(), e);
        }
    }


    /**
     * 解析以逗号分隔的邮箱地址：去除每个地址首尾空格、过滤空值并去重。
     *
     * @param emailAddresses 以逗号分隔的邮箱地址字符串
     * @return 规范化后的邮箱集合；没有有效邮箱时返回 null
     */
    private Set<String> parseEmailAddresses(String emailAddresses)
    {
        if (StringUtils.isBlank(emailAddresses))
        {
            return null;
        }
        Set<String> addresses = Arrays.stream(emailAddresses.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return addresses.isEmpty() ? null : addresses;
    }



}
