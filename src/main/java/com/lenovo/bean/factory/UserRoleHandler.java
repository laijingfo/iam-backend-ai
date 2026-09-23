package com.lenovo.bean.factory;

import com.lenovo.config.GlobalBusinessStatusEnum;
import com.lenovo.entity.Role;
import com.lenovo.mapper.RoleMapper;
import com.lenovo.service.RoleService;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description TODO 用户角色表处理器：封装用户角色表的角色过滤、创建等逻辑
 * @ClassName UserRoleHandler
 * @Author wangfenglong
 * @Date 2025/12/21 14:30
 **/
public class UserRoleHandler
{
    /**
     * 处理用户角色表的角色逻辑
     * @param dbRoles 从数据库获取的角色列表
     * @param existingRoles 已存在的角色列表
     * @param roleMapper 角色Mapper
     * @param roleService 角色服务
     * @return 处理后的角色列表
     */
    public static List<Role> handleUserRoles(List<Role> dbRoles, List<Role> existingRoles, RoleMapper roleMapper, RoleService roleService)
    {
        List<Role> processedRoles = new ArrayList<>();

        // 1. 判断已存在的角色是否包含Line_Manager和BPO
        boolean isMatchLm = RoleUtils.isRoleExist(existingRoles, GlobalBusinessStatusEnum.Line_Manager.desc);
        boolean isMatchBpo = RoleUtils.isRoleExist(existingRoles, GlobalBusinessStatusEnum.BPO.desc);

        // 2. 过滤掉数据库角色中重复的Line_Manager/BPO（如果已存在）
        List<Role> filteredRoles = filterDuplicateRoles(dbRoles, isMatchLm, isMatchBpo);

        // 3. 为每个过滤后的角色创建完整的角色对象
        for (Role dbRole : filteredRoles)
        {
            Role processedRole = createUserTableRole(dbRole, roleService);
            processedRoles.add(processedRole);
        }
        return processedRoles;
    }

    /**
     * 过滤掉重复的Line_Manager/BPO角色
     * @param dbRoles 数据库角色列表
     * @param hasLm 已存在Line_Manager角色
     * @param hasBpo 已存在BPO角色
     * @return 过滤后的角色列表
     */
    private static List<Role> filterDuplicateRoles(List<Role> dbRoles, boolean hasLm, boolean hasBpo)
    {
        List<Role> filtered = new ArrayList<>();
        for (Role role : dbRoles)
        {
            if (role == null || role.getName() == null)
            {
                continue;
            }
            // 如果已存在LM，过滤掉数据库中的LM角色
            if (hasLm && GlobalBusinessStatusEnum.Line_Manager.desc.equals(role.getName()))
            {
                continue;
            }
            // 如果已存在BPO，过滤掉数据库中的BPO角色
            if (hasBpo && GlobalBusinessStatusEnum.BPO.desc.equals(role.getName()))
            {
                continue;
            }
            filtered.add(role);
        }
        return filtered;
    }

    /**
     * 创建用户角色表的角色对象（补充完整的角色信息）
     * @param dbRole 数据库中的基础角色
     * @param roleService 角色服务
     * @return 完整的角色对象
     */
    private static Role createUserTableRole(Role dbRole, RoleService roleService)
    {
        Role currentRole = roleService.detail(String.valueOf(dbRole.getId()));
        Role processedRole = new Role();
        processedRole.setId(dbRole.getId());
        processedRole.setMenuKeys(dbRole.getMenuKeys());
        processedRole.setUserItem(currentRole.getUserItem());
        processedRole.setOrganizeItem(currentRole.getOrganizeItem());
        processedRole.setRoleMenuBeans(currentRole.getRoleMenuBeans());
        processedRole.setName(dbRole.getName());
        processedRole.setDelegationType(String.valueOf(GlobalBusinessStatusEnum.USER_ROLES_TABLE_ROLE_FLAG.code));
        return processedRole;
    }
}
