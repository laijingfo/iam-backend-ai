package com.lenovo.bean.factory;

import cn.hutool.core.collection.CollectionUtil;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.config.GlobalBusinessStatusEnum;
import com.lenovo.entity.Role;
import com.lenovo.entity.UserAccessReview;
import com.lenovo.mapper.BPOMapper;
import com.lenovo.mapper.RoleMapper;
import com.lenovo.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description TODO 动态授权策略：从user_access_review表获取角色
 * @ClassName DynamicAuthRoleStrategy
 * @Author wangfenglong
 * @Date 2025/12/21 14:00
 **/
@Component
@RequiredArgsConstructor
public class DynamicAuthRoleStrategy implements RoleRetrievalStrategy
{
    private final BPOMapper bpoMapper;
    private final RoleMapper roleMapper;
    private final RoleService roleService;

    @Override
    public List<Role> retrieveRoles(String itcode, List<Role> existingRoles)
    {
        List<Role> newRoles = new ArrayList<>();
        UseAccessReviewBean bean = new UseAccessReviewBean();
        bean.setLineManager(itcode);
        bean.setBpo(itcode);

        // 获取Line_Manager角色
        List<UserAccessReview> lmList = bpoMapper.getBPOReviewByLineManager(bean);
        if (CollectionUtil.isNotEmpty(lmList))
        {
            Role lmRole = RoleUtils.createRole(GlobalBusinessStatusEnum.Line_Manager.desc, roleMapper, roleService, String.valueOf(GlobalBusinessStatusEnum.DYNAMICALLY_FETCH_ROLE_FLAG.code));
            newRoles.add(lmRole);
        }

        // 获取BPO角色
        List<UserAccessReview> bpoList = bpoMapper.getBPOReviewByBpo(bean);
        if (CollectionUtil.isNotEmpty(bpoList))
        {
            Role bpoRole = RoleUtils.createRole(GlobalBusinessStatusEnum.BPO.desc, roleMapper, roleService, String.valueOf(GlobalBusinessStatusEnum.DYNAMICALLY_FETCH_ROLE_FLAG.code));
            newRoles.add(bpoRole);
        }

        return newRoles;
    }
}
