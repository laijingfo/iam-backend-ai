package com.lenovo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.bean.NaSensitiveAccessAlertBean;
import com.lenovo.dto.NaSensitiveAccessAlertRequest;
import com.lenovo.async.SendMailTask;
import com.lenovo.entity.NaSensitiveAccessAlert;
import com.lenovo.entity.SendEmailActionLog;
import com.lenovo.entity.UarMailTemplate;
import com.lenovo.mapper.NaSensitiveAccessAlertMapper;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.service.NaSensitiveAccessAlertService;
import com.lenovo.service.SendEmailActionLogService;
import com.lenovo.service.UarMailTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NaSensitiveAccessAlertServiceImpl
        extends ServiceImpl<NaSensitiveAccessAlertMapper, NaSensitiveAccessAlert>
        implements NaSensitiveAccessAlertService {

    static final String MAIL_TEMPLATE_TAG = "6";
    static final String MAIL_TEMPLATE_RECIPIENT = "ToUarProcessor";
    static final String MAIL_SEND_FLAG = "NaSensitiveAccessAlert";

    private final UarMailTemplateService uarMailTemplateService;
    private final SendMailTask sendMailTask;
    private final SendEmailActionLogService sendEmailActionLogService;

    @Override
    @Transactional
    public int createNewAlerts() {
        return baseMapper.insertNewAlerts("JOB");
    }

    @Override
    @Transactional
    public int resolveMissingAlerts() {
        return baseMapper.resolveMissingAlerts("JOB");
    }

    @Override
    public int sendPendingAlertEmails() {
        List<NaSensitiveAccessAlert> alerts = baseMapper.selectPendingAlertsToSend();
        if (alerts.isEmpty()) {
            return 0;
        }

        UarMailTemplate template = uarMailTemplateService.findTemplateWithMaxVersion(
                MAIL_TEMPLATE_TAG, MAIL_TEMPLATE_RECIPIENT);
        if (template == null) {
            String message = "未找到NA-Sensitive权限告警邮件模板";
            log.error("{}: tag={}, toSomeone={}", message, MAIL_TEMPLATE_TAG, MAIL_TEMPLATE_RECIPIENT);
            recordSendPreparationFailure("JOB", null, message);
            return 0;
        }

        int submittedCount = 0;
        for (NaSensitiveAccessAlert alert : alerts) {
            try {
                sendMailTask.sendNaSensitiveAccessAlertEmail(alert, template, MAIL_SEND_FLAG);
                submittedCount++;
            } catch (Exception e) {
                log.error("提交NA-Sensitive权限告警邮件任务失败: id={}", alert.getId(), e);
            }
        }
        return submittedCount;
    }

    /** 发送前缺少模板或收件人时，尚未进入统一发送入口，记录业务失败原因。 */
    private void recordSendPreparationFailure(String itCode, String batchNo, String message) {
        SendEmailActionLog actionLog = new SendEmailActionLog();
        actionLog.setItCode(itCode);
        actionLog.setOperation("发送NA-Sensitive权限告警邮件失败");
        actionLog.setSendFlag(MAIL_SEND_FLAG);
        actionLog.setMessage(StringUtils.defaultIfBlank(message, "邮件发送失败"));
        actionLog.setBatchNo(batchNo);
        actionLog.setCreateDate(LocalDateTime.now());
        sendEmailActionLogService.addSendEmailLog(actionLog);
    }

    @Override
    public Page<NaSensitiveAccessAlertBean> getList(NaSensitiveAccessAlertRequest request) {
        Page<NaSensitiveAccessAlertBean> page = new Page<>(request.getPage(), request.getSize(), false);
        Page<NaSensitiveAccessAlertBean> result = baseMapper.selectAlertPage(page, request);
        result.setTotal(baseMapper.selectAlertCount(request));
        return result;
    }

    @Override
    public List<NaSensitiveAccessAlertBean> getAlertsForExport(NaSensitiveAccessAlertRequest request) {
        return baseMapper.selectAlertsForExport(request);
    }

    @Override
    @Transactional
    public boolean suppressAlert(Long id, String suppressedReason, String suppressedRequestedBy, List<String> dataRange) {
        if (StringUtils.isBlank(suppressedReason)) {
            throw new IllegalArgumentException("Suppressed Reason不能为空");
        }
        String operator = SecurityUtils.getCurrentUserId();
        // 告警抑制申请人：前端未显式传入时，默认取当前操作人
        String requestedBy = StringUtils.defaultIfBlank(suppressedRequestedBy, operator);
        return baseMapper.suppressAlert(id, suppressedReason.trim(), requestedBy.trim(), operator, dataRange) > 0;
    }
}
