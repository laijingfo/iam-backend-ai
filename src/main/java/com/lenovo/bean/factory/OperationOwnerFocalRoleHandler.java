package com.lenovo.bean.factory;

import com.lenovo.config.GlobalBusinessStatusEnum;
import com.lenovo.entity.Role;
import com.lenovo.mapper.RoleMapper;
import com.lenovo.service.RoleService;

import java.util.List;

/**
 * @Description TODO OPERATION_OWNER_FOCAL角色处理器：封装operation_owner和operation_focal角色的创建、cmdbId更新等逻辑
 * @ClassName OperationOwnerFocalRoleHandler
 * @Author wangfenglong
 * @Date 2026/1/22 12:29
 **/
public class OperationOwnerFocalRoleHandler
{
    /**
     * 处理OperationOwnerFocal角色逻辑
     * @param existingRoles 已存在的角色列表
     * @param cmdbIdStr 拼接后的cmdbId字符串
     * @param roleMapper 角色Mapper
     * @param roleService 角色服务
     * @return 处理后的OperationOwnerFocal角色列表
     */
    public static List<Role> handleOperationOwnerFocalRole(List<Role> existingRoles, String cmdbIdStr, RoleMapper roleMapper, RoleService roleService)
    {
        // 1. 判断已存在的角色是否包含OPERATION_OWNER_FOCAL
        boolean isMatch = RoleUtils.isRoleExist(existingRoles, GlobalBusinessStatusEnum.OPERATION_OWNER_FOCAL.desc);
        if (isMatch)
        {
            // 2. 已存在，更新cmdbId并返回空列表（因为是更新原有角色，无需新增）
            updateUarProcesserCmdbId(existingRoles, cmdbIdStr);
            return List.of();
        }
        else
        {
            // 3. 不存在，创建新的OperationOwnerFocal角色并返回
            Role uarRole = createUarProcesserRole(cmdbIdStr, roleMapper, roleService);
            return List.of(uarRole);
        }
    }

    /**
     * 更新已存在的OperationOwnerFocal角色的cmdbId
     * @param existingRoles 已存在的角色列表
     * @param cmdbIdStr 拼接后的cmdbId字符串
     */
    private static void updateUarProcesserCmdbId(List<Role> existingRoles, String cmdbIdStr)
    {
        for (Role role : existingRoles)
        {
            if (role != null && GlobalBusinessStatusEnum.OPERATION_OWNER_FOCAL.desc.equals(role.getName()))
            {
                role.setCmdbIdOfOperationOwnerFocalRole(cmdbIdStr);
                break;
            }
        }
    }

    /**
     * 创建新的OperationOwnerFocal角色
     * @param cmdbIdStr 拼接后的cmdbId字符串
     * @param roleMapper 角色Mapper
     * @param roleService 角色服务
     * @return OperationOwnerFocal角色
     */
    private static Role createUarProcesserRole(String cmdbIdStr, RoleMapper roleMapper, RoleService roleService)
    {
        // 使用工具类创建基础角色
        Role uarRole = RoleUtils.createRole(GlobalBusinessStatusEnum.OPERATION_OWNER_FOCAL.desc, roleMapper, roleService, String.valueOf(GlobalBusinessStatusEnum.UAR_PROCESSER_FLAG.code));
        // 设置cmdbId
        uarRole.setCmdbIdOfOperationOwnerFocalRole(cmdbIdStr);
        return uarRole;
    }
}
