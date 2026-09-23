package com.lenovo.strategy;

/**
 * @Description TODO 发送标志
 * @author wangfenglong
 * @date 2026/4/22 12:16
**/
public enum SendEmailStrategyType
{
    USER_CYCLE_TYPE,
    DELEGATION_CYCLE_TYPE,
    EXPIRED_BPO_CYCLE_TYPE,
    UAR_CYCLE_MAINTENANCE_TYPE,
    UAR_FINAL_REMOVE_USER_TYPE,
    UAR_LM_SEND_TYPE,
    UAR_BPO_SEND_TYPE,
    UAR_REVIEW_REMOVE_USER_SEND_TYPE  //Amani BEYOND
}
