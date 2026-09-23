package com.lenovo.service.impl;

import cn.hutool.core.date.StopWatch;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.bean.AdvancedSearchBean;
import com.lenovo.bean.BpoItCodeExpireEmailData;
import com.lenovo.bean.UarAlertBean;
import com.lenovo.bean.UarAlertBpoBean;
import com.lenovo.constant.UarAlertType;
import com.lenovo.dto.MarkBpoInvalidRequest;
import com.lenovo.dto.UarAlertRequest;
import com.lenovo.entity.SendEmailActionLog;
import com.lenovo.entity.UarMailTemplate;
import com.lenovo.entity.UserAccessReviewAlert;
import com.lenovo.mapper.AlertDashboardMapper;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.service.*;
import com.lenovo.util.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertDashboardServiceImpl extends ServiceImpl<AlertDashboardMapper, UserAccessReviewAlert> implements AlertDashboardService
{
    private final UarMailService uarMailService;
    private final SendEmailActionLogService sendEmailActionLogService;

    /**
     * itCode检测任务
     *
     * @return
     */
    @Override
    @Transactional
    public int insertUarAlertData() {

        Map<UarAlertType, Integer> detectedRows = detectUarAlerts("JOB");
        int archivedRows = baseMapper.deleteSourceData();
        int totalDetectedRows = detectedRows.values().stream().mapToInt(Integer::intValue).sum();

        log.info("itCode检测任务完成,告警数:{},归档源数据数:{},各类型告警:{}",
                totalDetectedRows, archivedRows, detectedRows);
        return archivedRows;
    }

    /**
     * 按业务优先级生成告警。
     * <p>
     * 去重规则会排除已有未完成告警的 UAR 记录，因此这里的顺序是有业务含义的，
     * 不要随意调整。
     */
    private Map<UarAlertType, Integer> detectUarAlerts(String operator) {
        Map<UarAlertType, Integer> detectedRows = new LinkedHashMap<>();
        for (UarAlertType alertType : UarAlertType.automaticDetectionOrder()) {
            detectedRows.put(alertType, baseMapper.insertUarAlertData(operator, alertType.getCode()));
        }
        return detectedRows;
    }

    @Override
    public Page<UarAlertBean> getList(UarAlertRequest uarAlertRequest) {
        Page<UarAlertBean> page = new Page<>(uarAlertRequest.getPage(), uarAlertRequest.getSize(), false);
        Page<UarAlertBean> result = baseMapper.getList(page, uarAlertRequest);
        result.setTotal(baseMapper.getListCount(uarAlertRequest));
        return result;
    }

    @Override
    @Transactional
    public int handle() {
        int update = 0;

        // userItCode 失效
        update += this.handleUserItCodeAlert();

        // line_manager ItCode 失效 || line_manager 变更
        update += this.handleLineManagerItCodeAlert();

        // BPO ItCode 失效 || 手工标记BPO失效
        update += this.handleBpoInvalidAlert();

        return update;
    }

    /**
     * 处理userItCode失效
     */
    @Override
    @Transactional
    public int handleUserItCodeAlert() {
        // 把源数据恢复并且操作审核结果数据
        return baseMapper.restoreDataAndUpdateReview();
    }

    /**
     * 处理line_manager ItCode 失效
     */
    @Override
    @Transactional
    public int handleLineManagerItCodeAlert() {
        // 把源数据恢复并且调整直属经理
        return baseMapper.restoreDataAndUpdateLineManager();
    }

    /**
     * 处理BPO ItCode 失效
     */
    @Override
    @Transactional
    public int handleBpoInvalidAlert() {
        long countInvalidBpo = baseMapper.countInvalidBpo();
        if (countInvalidBpo < 1) {
            return 0;
        }

        int updated = 0;
        try {
            // 对已有数据进行恢复
            updated = baseMapper.restoreDataAndUpdateBPO();
        } catch (Exception e) {
            log.error("处理人工标记BPO失效告警失败", e);
        }

        return updated;
    }

    @Override
    @Transactional
    public int insertManualBpoInvalid(MarkBpoInvalidRequest dto) {
        String operator = SecurityUtils.getCurrentUsername();
        int inserted = baseMapper.insertManualBpoInvalid(operator, dto);
        if (inserted > 0) {
            baseMapper.deleteSourceData();
        }
        return inserted;
    }

    /**
     * 发送bpo中ItCode失效的邮件给app的拥有者
     */
    private int sendBpoInvalidEmailToAppOwner() {
        return 0;
    }


    /**
     * @Description TODO 发送邮件
     * @author wangfenglong
     * @date 2026/3/4 16:16
    **/
    @Async
    @Override
    public void handleExpiredBpoNewFunc(String itCode, List<BpoItCodeExpireEmailData> dataList, UarMailTemplate bpoTemplate, String batchNo, String sendFlag)
    {
        try
        {
            int total = dataList.size();
            for(BpoItCodeExpireEmailData data : dataList)
            {
                //String focalOwnerEmail = data.getEmailName()+"@lenovo.com";
                String focalOwnerEmail = data.getEmail();
                Map<String, String> taskVariables = new HashMap<>(data.getVariables());
                String itCodeValue = null;
                itCodeValue = data.getEmailName();
                taskVariables.put("{itCode}", itCodeValue);
                final Map<String, String> finalTaskVariables = taskVariables;
                try
                {
                    uarMailService.sendTemplatedEmailOnlyForLmBpoAndUARSetting(bpoTemplate, Collections.singleton(focalOwnerEmail), null, finalTaskVariables, batchNo,sendFlag);
                }
                catch (Exception e)
                {
                    log.error("处理BPO_ITCode失效：发送邮件：执行单条邮件发送任务失败{}",focalOwnerEmail, e);
                    SendEmailActionLog sendEmailActionLog = new SendEmailActionLog();
                    sendEmailActionLog.setItCode(itCode);
                    sendEmailActionLog.setOperation("处理BPO_ITCode失效发送邮件失败:用户邮箱:" + focalOwnerEmail);
                    sendEmailActionLog.setSendFlag(sendFlag);
                    sendEmailActionLog.setMessage(e.getMessage());
                    sendEmailActionLog.setStackTrace(Arrays.toString(e.getStackTrace()));
                    sendEmailActionLog.setBatchNo(batchNo);
                    sendEmailActionLog.setCreateDate(LocalDateTime.now());
                    sendEmailActionLogService.addSendEmailLog(sendEmailActionLog);
                }
            }
            log.info("处理BPO_ITCode失效：发送邮件：邮件发送批次任务完成【批次号：{}】，共{}条 ",batchNo,total);
        }
        catch (Exception e)
        {
            log.error("处理BPO_ITCode失效:邮件发送失败: errorMsg={}", e.getMessage());
            SendEmailActionLog sendEmailActionLog = new SendEmailActionLog();
            sendEmailActionLog.setItCode(itCode);
            sendEmailActionLog.setOperation("处理BPO_ITCode失效发送邮件失败。");
            sendEmailActionLog.setSendFlag(sendFlag);
            sendEmailActionLog.setMessage(e.getMessage());
            sendEmailActionLog.setStackTrace(Arrays.toString(e.getStackTrace()));
            sendEmailActionLog.setBatchNo(batchNo);
            sendEmailActionLog.setCreateDate(LocalDateTime.now());
            sendEmailActionLogService.addSendEmailLog(sendEmailActionLog);
        }
    }




    @Override
    public List<UarAlertBean> getAlertsForExport(UarAlertRequest uarAlertRequest) {
        return baseMapper.getAlertsForExport(uarAlertRequest);
    }

    @Override
    public Page<UarAlertBpoBean> getAlertBpoSummary(UarAlertRequest uarAlertRequest) {
        Page<UarAlertBpoBean> page = new Page<>(uarAlertRequest.getPage(), uarAlertRequest.getSize());
        return baseMapper.getAlertBpoSummary(page, uarAlertRequest);
    }

    @Override
    public List<UarAlertBean> getAllAlertBpoSummary(UarAlertRequest uarAlertRequest) {
        return baseMapper.getAllAlertBpoSummary(uarAlertRequest);
    }

    @Override
    public List<AdvancedSearchBean> getAlertInvalidBpoList(UarAlertRequest uarAlertRequest) {
        return baseMapper.getAlertInvalidBpoList(uarAlertRequest);
    }
}
