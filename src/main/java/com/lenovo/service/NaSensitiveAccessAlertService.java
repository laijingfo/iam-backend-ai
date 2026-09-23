package com.lenovo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.NaSensitiveAccessAlertBean;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lenovo.dto.NaSensitiveAccessAlertRequest;
import com.lenovo.entity.NaSensitiveAccessAlert;

import java.util.List;

public interface NaSensitiveAccessAlertService extends IService<NaSensitiveAccessAlert> {
    /** 创建新的 COC & NA-Sensitive 告警数据。 */
    int createNewAlerts();

    /** 处理 COC & NA-Sensitive 的告警数据。 */
    int resolveMissingAlerts();

    /** 提交待发送告警至邮件线程池，返回成功提交的任务数量。 */
    int sendPendingAlertEmails();

    Page<NaSensitiveAccessAlertBean> getList(NaSensitiveAccessAlertRequest request);

    List<NaSensitiveAccessAlertBean> getAlertsForExport(NaSensitiveAccessAlertRequest request);

    boolean suppressAlert(Long id, String suppressedReason, String suppressedRequestedBy, List<String> dataRange);
}
