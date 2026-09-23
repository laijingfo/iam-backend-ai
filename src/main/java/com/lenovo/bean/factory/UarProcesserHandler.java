package com.lenovo.bean.factory;

import com.lenovo.config.GlobalBusinessStatusEnum;
import com.lenovo.entity.Role;
import com.lenovo.mapper.RoleMapper;
import com.lenovo.service.RoleService;

import java.util.List;

/**
 * @Description TODO UARProcesser角色处理器：封装UARProcesser角色的创建、cmdbId更新等逻辑
 * @ClassName UarProcesserHandler
 * @Author wangfenglong
 * @Date 2025/12/21 14:32
 **/
public class UarProcesserHandler
{
    /**
     * 处理UARProcesser角色逻辑
     * @param existingRoles 已存在的角色列表
     * @param cmdbIdStr 拼接后的cmdbId字符串
     * @param roleMapper 角色Mapper
     * @param roleService 角色服务
     * @return 处理后的UARProcesser角色列表
     */
    public static List<Role> handleUarProcesserRole(List<Role> existingRoles, String cmdbIdStr, RoleMapper roleMapper, RoleService roleService)
    {
        // 1. 判断已存在的角色是否包含UAR_PROCESSER
        boolean isMatch = RoleUtils.isRoleExist(existingRoles, GlobalBusinessStatusEnum.UAR_PROCESSER.desc);

        if (isMatch)
        {
            // 2. 已存在，更新cmdbId并返回空列表（因为是更新原有角色，无需新增）
            updateUarProcesserCmdbId(existingRoles, cmdbIdStr);
            return List.of();
        }
        else
        {
            // 3. 不存在，创建新的UARProcesser角色并返回
            Role uarRole = createUarProcesserRole(cmdbIdStr, roleMapper, roleService);
            return List.of(uarRole);
        }
    }

    /**
     * 更新已存在的UARProcesser角色的cmdbId
     * @param existingRoles 已存在的角色列表
     * @param cmdbIdStr 拼接后的cmdbId字符串
     */
    private static void updateUarProcesserCmdbId(List<Role> existingRoles, String cmdbIdStr)
    {
        for (Role role : existingRoles)
        {
            if (role != null && GlobalBusinessStatusEnum.UAR_PROCESSER.desc.equals(role.getName()))
            {
                role.setCmdbIdOfUarProcesser(cmdbIdStr);
                break;
            }
        }
    }

    /**
     * 创建新的UARProcesser角色
     * @param cmdbIdStr 拼接后的cmdbId字符串
     * @param roleMapper 角色Mapper
     * @param roleService 角色服务
     * @return UARProcesser角色
     */
    private static Role createUarProcesserRole(String cmdbIdStr, RoleMapper roleMapper, RoleService roleService)
    {
        // 使用工具类创建基础角色
        Role uarRole = RoleUtils.createRole(GlobalBusinessStatusEnum.UAR_PROCESSER.desc, roleMapper, roleService, String.valueOf(GlobalBusinessStatusEnum.UAR_PROCESSER_FLAG.code));
        // 设置cmdbId
        uarRole.setCmdbIdOfUarProcesser(cmdbIdStr);
        return uarRole;
    }
}
