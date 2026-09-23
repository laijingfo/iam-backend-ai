package com.lenovo.config;

import java.lang.annotation.*;

/**
 * @Description TODO 自定义权限校验注解
 * @ClassName RequiresPermission
 * @Author wangfenglong
 * @Date 2026/4/20 14:27
 **/
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission
{
    /**
     * 需要的角色（如 "admin"、"manager"），支持多角色配置
     * 默认为空数组，表示不校验角色
     */
    String[] roles()default{};

    /**
     * 需要的权限码（如 "user:add"、"user:delete"），支持多权限码配置
     * 默认为空数组，表示不校验权限码
     */
    String[] permissions()default{};

    /**
     * 校验逻辑：AND（所有条件必须满足）、OR（满足任一条件即可）
     * 默认为 AND，即角色和权限码都满足时，才允许访问
     */
    Logical logical()default Logical.AND;

    /**
     * 是否忽略超级管理员校验（默认不忽略）
     * 若为 true，超级管理员无需满足角色和权限码，直接放行
     */
    boolean ignoreSuperAdmin()default true;

    /**
     * 是否在用户包含Line_Manager或BPO角色时自动设置ssFlag参数为"1"
     * 默认为 false
     */
    boolean autoSetSsFlag()default false;
}
