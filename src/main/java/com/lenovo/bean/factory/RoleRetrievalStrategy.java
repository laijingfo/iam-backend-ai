package com.lenovo.bean.factory;

import com.lenovo.entity.Role;

import java.util.List;

/**
 * @Description TODO 角色获取策略接口
 * @author wangFenglong
 * @date 2025/12/21 13:59
**/
public interface RoleRetrievalStrategy
{
    /**
     * 执行策略，获取角色列表
     * @param itCode 用户itcode
     * @param existingRoles 已存在的角色列表（用于累加）
     * @return 新增的角色列表
     */
    List<Role> retrieveRoles(String itCode, List<Role> existingRoles);
}
