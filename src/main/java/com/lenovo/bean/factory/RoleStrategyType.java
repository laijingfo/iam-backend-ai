package com.lenovo.bean.factory;

/**
 * @Description TODO 需要获取获取的角色
 * @ClassName RoleStrategyType
 * @Author wangfenglong
 * @Date 2025/12/21 14:41
 **/
public enum RoleStrategyType
{
    DYNAMIC_AUTH,      // 动态授权
    DELEGATION,        // 委托授权
    USER_ROLE_TABLE,   // 用户角色表
    UAR_PROCESSER,
    OPERATION_OWNER_FOCAL //operationOwner和operationFocal都属于这个角色(这个角色配置在角色管理里面 是系统角色)
}
