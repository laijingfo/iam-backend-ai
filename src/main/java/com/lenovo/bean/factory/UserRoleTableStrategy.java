package com.lenovo.bean.factory;

import cn.hutool.core.collection.CollectionUtil;
import com.lenovo.config.GlobalBusinessStatusEnum;
import com.lenovo.entity.Role;
import com.lenovo.mapper.RoleMapper;
import com.lenovo.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Description TODO 用户角色表策略：从sys_user_roles表获取角色
 * @ClassName UserRoleTableStrategy
 * @Author wangfenglong
 * @Date 2025/12/21 14:11
 **/
@Component
@RequiredArgsConstructor
public class UserRoleTableStrategy implements RoleRetrievalStrategy
{
    private final RoleMapper roleMapper;
    private final RoleService roleService;

    @Override
    public List<Role> retrieveRoles(String itcode, List<Role> existingRoles)
    {
        //将该itCode下的所有关联的角色都查询出来
        List<Role> dbRoles = roleMapper.findRoleByUserId(itcode, GlobalBusinessStatusEnum.USER_TYPE.desc);
        if (CollectionUtil.isEmpty(dbRoles))
        {
            // 返回默认AccessUser角色
            Role defaultRole = RoleUtils.createRole(GlobalBusinessStatusEnum.Access_User.desc, roleMapper, roleService, String.valueOf(GlobalBusinessStatusEnum.SYSTEM_DEFAULT_ROLE_FLAG.code));
            return List.of(defaultRole);
        }

        // 处理已存在的角色，移除重复的Line_Manager/BPO
        return UserRoleHandler.handleUserRoles(dbRoles, existingRoles, roleMapper, roleService);
    }

}
