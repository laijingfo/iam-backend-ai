package com.lenovo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lenovo.entity.SendEmailActionLog;

/**
 * @author wangFenglong
 * @data 2026/1/20
 **/
public interface SendEmailActionLogService extends IService<SendEmailActionLog>
{
    void addSendEmailActionLog(SendEmailActionLog sendEmailActionLog);

    void addSendEmailLog(SendEmailActionLog sendEmailActionLog);
}
