package com.lenovo.bean.factory;

import com.lenovo.config.GlobalBusinessStatusEnum;
import com.lenovo.entity.Delegation;
import com.lenovo.entity.Role;
import com.lenovo.mapper.RoleMapper;
import com.lenovo.service.RoleService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Description TODO 处理委托授权的角色逻辑
 * @ClassName DelegationRoleHandler
 * @Author wangfenglong
 * @Date 2025/12/21 14:24
 **/
public class DelegationRoleHandler
{
    /**
     * 处理委托授权的角色逻辑
     * @param delegationMap 按授权范围分组的委托记录
     * @param existingRoles 已存在的角色列表
     * @param roleMapper 角色Mapper
     * @param roleService 角色服务
     * @return 处理后的角色列表
     */
    public static List<Role> handleDelegationRoles(Map<String, List<Delegation>> delegationMap, List<Role> existingRoles, RoleMapper roleMapper, RoleService roleService)
    {
        List<Role> newRoles = new ArrayList<>();

        // 遍历每个授权范围
        for (Map.Entry<String, List<Delegation>> entry : delegationMap.entrySet())
        {
            String scope = entry.getKey();
            List<Delegation> delegationList = entry.getValue();

            // 处理Line_Manager授权范围
            if (GlobalBusinessStatusEnum.Line_Manager.desc.equals(scope))
            {
                handleLineManagerDelegation(existingRoles, newRoles, delegationList, roleMapper, roleService);
            }
            // 处理BPO授权范围
            else if (GlobalBusinessStatusEnum.BPO.desc.equals(scope))
            {
                handleBpoDelegation(existingRoles, newRoles, delegationList, roleMapper, roleService);
            }
            // 处理LineManager_BPO授权范围（同时包含LM和BPO）
            else if (GlobalBusinessStatusEnum.LineManager_BPO.desc.equals(scope))
            {
                handleLineManagerBpoDelegation(existingRoles, newRoles, delegationList, roleMapper, roleService);
            }
        }
        return newRoles;
    }

    /**
     * 处理Line_Manager授权范围的逻辑
     */
    private static void handleLineManagerDelegation(List<Role> existingRoles, List<Role> newRoles, List<Delegation> delegationList, RoleMapper roleMapper, RoleService roleService)
    {
        String delegatorName = RoleUtils.buildDelegatorName(delegationList);
        boolean isExist = RoleUtils.isRoleExist(existingRoles, GlobalBusinessStatusEnum.Line_Manager.desc);

        if (isExist)
        {
            // 已存在该角色，合并授权人
            for (Role role : existingRoles)
            {
                if (GlobalBusinessStatusEnum.Line_Manager.desc.equals(role.getName()))
                {
                    String mergedDelegator = RoleUtils.mergeDelegator(role.getDelegator(), delegatorName);
                    role.setDelegator(mergedDelegator);
                    //user_access_review里已经有该角色了，所以就不能覆盖之前被赋的值
                    //role.setDelegationType(String.valueOf(GlobalBusinessStatusEnum.DELEGATION_ROLE_FLAG.code));
                }
            }
        }
        else
        {
            // 不存在该角色，创建新角色并添加授权人
            Role lmRole = createDelegationRole(GlobalBusinessStatusEnum.Line_Manager.desc, delegatorName, roleMapper, roleService);
            newRoles.add(lmRole);
        }
    }

    /**
     * 处理BPO授权范围的逻辑
     */
    private static void handleBpoDelegation(List<Role> existingRoles, List<Role> newRoles, List<Delegation> delegationList, RoleMapper roleMapper, RoleService roleService)
    {
        String delegatorName = RoleUtils.buildDelegatorName(delegationList);
        boolean isExist = RoleUtils.isRoleExist(existingRoles, GlobalBusinessStatusEnum.BPO.desc);

        if (isExist)
        {
            // 已存在该角色，合并授权人
            for (Role role : existingRoles)
            {
                if (GlobalBusinessStatusEnum.BPO.desc.equals(role.getName()))
                {
                    String mergedDelegator = RoleUtils.mergeDelegator(role.getDelegator(), delegatorName);
                    role.setDelegator(mergedDelegator);
                    //user_access_review里已经有该角色了，所以就不能覆盖之前被赋的值
                    //role.setDelegationType(String.valueOf(GlobalBusinessStatusEnum.DELEGATION_ROLE_FLAG.code));
                }
            }
        }
        else
        {
            // 不存在该角色，创建新角色并添加授权人
            Role bpoRole = createDelegationRole(GlobalBusinessStatusEnum.BPO.desc, delegatorName, roleMapper, roleService);
            newRoles.add(bpoRole);
        }
    }

    /**
     * 处理LineManager_BPO授权范围的逻辑（同时创建LM和BPO角色）
     */
    private static void handleLineManagerBpoDelegation(List<Role> existingRoles, List<Role> newRoles, List<Delegation> delegationList, RoleMapper roleMapper, RoleService roleService)
    {
        String delegatorName = RoleUtils.buildDelegatorName(delegationList);

        // 处理Line_Manager角色
        handleLineManagerDelegation(existingRoles, newRoles, delegationList, roleMapper, roleService);
        // 处理BPO角色
        handleBpoDelegation(existingRoles, newRoles, delegationList, roleMapper, roleService);
    }

    /**
     * 创建委托授权的角色对象
     * @param roleName 角色名称（Line_Manager/BPO）
     * @param delegatorName 授权人字符串
     * @param roleMapper 角色Mapper
     * @param roleService 角色服务
     * @return 委托授权角色
     */
    private static Role createDelegationRole(String roleName, String delegatorName, RoleMapper roleMapper, RoleService roleService)
    {
        // 使用工具类创建基础角色
        Role role = RoleUtils.createRole(roleName, roleMapper, roleService, String.valueOf(GlobalBusinessStatusEnum.DELEGATION_ROLE_FLAG.code));
        // 设置授权人
        role.setDelegator(delegatorName);
        return role;
    }
}
