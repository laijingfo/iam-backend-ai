package com.lenovo.strategy;

import com.lenovo.dto.BatchSendRequest;

/**
 * @Description TODO 邮件发送策略接口
 * @ClassName CheckStrategy
 * @Author wangfenglong
 * @Date 2026/4/22 14:02
 **/
public interface CheckStrategy<T,E>
{
    // [优化] 不懂为什么要定义两个参数
    E handle(BatchSendRequest request,T param);
    SendEmailStrategyType getType();
}
