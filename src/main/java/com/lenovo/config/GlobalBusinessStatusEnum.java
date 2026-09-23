package com.lenovo.config;

/**
 * @Description TODO 全局业务状态枚举（公共枚举类）
 * 说明：所有服务可直接依赖该枚举，通过静态方法获取枚举值，无需重复定义
 * @author wangfenglong
 * @date 2025/11/19 15:58
**/
public enum GlobalBusinessStatusEnum
{
    ADMIN(26, "admin"),
    BPO(53, "BPO"),
    Line_Manager(52, "Line_Manager"),
    LineManager_BPO(1123456, "Lm+BPO"),
    Access_User(55, "Access_User"),
    UAR_PROCESSER(65,"UAR_Processer"),
    OPERATION_OWNER_FOCAL(66,"Operation_owner_focal"),
    Platform_Administrator(29, "平台管理员"),

    USER_TYPE(123,"ADFS"),
    DELEGATION_ROLE_FLAG(1,"当前用户被授权的角色"),
    DYNAMICALLY_FETCH_ROLE_FLAG(2,"动态获取当前用户角色"),
    USER_ROLES_TABLE_ROLE_FLAG(3,"从用户角色表获取当前用户角色"),
    UAR_PROCESSER_FLAG(5,"从应用数据表获取的角色"),
    SYSTEM_DEFAULT_ROLE_FLAG(4,"系统默认角色"),

    REDIS_KEY_USER_ROLE(100,"_currentUserRolesRedis"),

    //分布式锁key（唯一标识缓存表写入操作）
    EMAIL_CACHE_WRITE_LOCK_KEY(2026,"email:cache:write:lock"),
    //锁超时时间（防止死锁，需大于定时任务执行周期）
    LOCK_TIMEOUT(60,"60"),
    //邮件超时时间（3分钟，可配置在yml中）
    MAIL_TIMEOUT_SECONDS(180,"180"),
    //批次大小(定时任务发送邮件)
    BATCH_SIZE(200,"200"),

    //日志颜色
    COLOR_RESET(1025,"\u001B[0m"),
    COLOR_GREEN(1024,"\u001B[32m"),

    //数据周期类型
    UAR_CYCLE_TYPE(1,"uar"),
    LR_CYCLE_TYPE(2,"lr");



    public final Integer code;
    public final String desc;

    GlobalBusinessStatusEnum(Integer code, String desc)
    {
        this.code = code;
        this.desc = desc;
    }

    public GlobalBusinessStatusEnum getByCode(Integer code)
    {
        return null;
    }
}
