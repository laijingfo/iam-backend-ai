package com.lenovo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.entity.SendEmailActionLog;
import com.lenovo.mapper.SendEmailActionLogMapper;
import com.lenovo.service.SendEmailActionLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;


/**
 * @Description TODO
 * @ClassName SendEmailActionLogServiceImpl
 * @Author wangfenglong
 * @Date 2026/1/20 16:30
 **/
@Slf4j
@Service
public class SendEmailActionLogServiceImpl extends ServiceImpl<SendEmailActionLogMapper, SendEmailActionLog> implements SendEmailActionLogService
{
    @Async
    @Override
    public void addSendEmailActionLog(SendEmailActionLog sendEmailActionLog)
    {
        getBaseMapper().insert(sendEmailActionLog);
    }

    @Async
    @Override
    public void addSendEmailLog(SendEmailActionLog sendEmailActionLog)
    {
        getBaseMapper().addSendEmailLog( sendEmailActionLog );
    }


}
