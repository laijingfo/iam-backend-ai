package com.lenovo.bean.factory;

import com.lenovo.entity.Delegation;
import com.lenovo.entity.Role;
import com.lenovo.mapper.RoleMapper;
import com.lenovo.service.RoleService;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Description TODO 角色工具类：封装重复的角色创建、判断逻辑
 * @ClassName RoleUtils
 * @Author wangfenglong
 * @Date 2025/12/21 14:19
 **/
public class RoleUtils
{

    /**
     * 通用创建角色的方法
     * @param roleName 角色名称
     * @param roleMapper 角色Mapper
     * @param roleService 角色服务
     * @param delegationType 角色类型（动态/委托/默认）
     * @return 构建好的角色对象
     */
    public static Role createRole(String roleName, RoleMapper roleMapper, RoleService roleService, String delegationType)
    {
        Role roleId = roleMapper.getRoleIdByName(roleName);
        Role currentRole = roleService.detail(String.valueOf(roleId.getId()));
        Role role = new Role();
        role.setId(roleId.getId());
        role.setMenuKeys(roleMapper.getOne(String.valueOf(roleId.getId())).getMenuKeys());
        role.setUserItem(currentRole.getUserItem());
        role.setOrganizeItem(currentRole.getOrganizeItem());
        role.setRoleMenuBeans(currentRole.getRoleMenuBeans());
        role.setName(currentRole.getName());
        role.setDelegationType(delegationType);
        return role;
    }

    /**
     * 判断角色列表中是否包含指定名称的角色
     * @param roles 角色列表
     * @param roleName 角色名称
     * @return 是否包含
     */
    public static boolean isRoleExist(List<Role> roles, String roleName)
    {
        return roles.stream()
                .filter(role -> role != null && role.getName() != null)
                .anyMatch(role -> role.getName().equals(roleName));
    }

    /**
     * 拼接授权人字符串（去重、去末尾逗号）
     * @param delegations 授权记录列表
     * @return 拼接后的授权人字符串
     */
    public static String buildDelegatorName(List<Delegation> delegations)
    {
        return delegations.stream()
                .map(Delegation::getDelegator)
                .distinct()
                .collect(Collectors.joining(","));
    }

    /**
     * 合并授权人字符串（原有+新增）
     * @param existing 原有授权人
     * @param newDelegator 新增授权人
     * @return 合并后的字符串
     */
    public static String mergeDelegator(String existing, String newDelegator)
    {
        if (existing == null || existing.isEmpty())
        {
            return newDelegator;
        }
        return String.join(",", existing, newDelegator);
    }

    /**
     * 判断Role列表是否仅包含唯一一个Role对象，多个对象说明可能包含Access_User角色，所以删除包含Access_User的角色；角色列表中如果有多个角色，则Access_User角色已经无意义所以删除；一个对象则不做任何操作
     * @param roleListForCurrentRole 待判断的Role列表
     * @return true=满足条件，false=不满足（null/空/多元素/name不匹配等）
     */
    public static List<Role> isOnlyContainAccessUser(List<Role> roleListForCurrentRole)
    {
        // 1. 校验列表本身：null 或 元素数量不等于1 → 直接返回false
        if (roleListForCurrentRole != null && roleListForCurrentRole.size() != 1)
        {
            //当前列表仅一个元素则不操作。当前列表有多个角色，则删除Access_User角色
            Iterator<Role> iterator = roleListForCurrentRole.iterator();
            while (iterator.hasNext())
            {
                Role role = iterator.next();
                //处理元素为null的情况
                if (role == null)
                {
                    continue;
                }
                //校验name是否等于"Access_User"
                if ("Access_User".equals(role.getName()))
                {
                    iterator.remove(); // 迭代器的remove方法安全删除当前元素
                }
            }
        }
        return roleListForCurrentRole;
    }
}
