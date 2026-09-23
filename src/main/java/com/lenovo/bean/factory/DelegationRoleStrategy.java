package com.lenovo.bean.factory;

import cn.hutool.core.collection.CollectionUtil;
import com.lenovo.bean.DelegationBean;
import com.lenovo.entity.Delegation;
import com.lenovo.entity.Role;
import com.lenovo.mapper.DelegationMapper;
import com.lenovo.mapper.RoleMapper;
import com.lenovo.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Description TODO 委托授权策略：从delegation表获取角色
 * @ClassName DelegationRoleStrategy
 * @Author wangfenglong
 * @Date 2025/12/21 14:07
 **/
@Component
@RequiredArgsConstructor
public class DelegationRoleStrategy implements RoleRetrievalStrategy
{
    private final DelegationMapper delegationMapper;
    private final RoleMapper roleMapper;
    private final RoleService roleService;

    @Override
    public List<Role> retrieveRoles(String itcode, List<Role> existingRoles)
    {
        DelegationBean bean = new DelegationBean();
        bean.setDelegatee(itcode);
        List<Delegation> delegationList = delegationMapper.getDelegationByDelegateeAndDelegationDate(bean);

        if (CollectionUtil.isEmpty(delegationList))
        {
            return List.of();
        }

        // 按授权范围分组
        Map<String, List<Delegation>> delegationMap = delegationList.stream()
                .filter(record -> record.getDelegationScope() != null && !record.getDelegationScope().trim().isEmpty())
                .collect(Collectors.groupingBy(record -> record.getDelegationScope().trim()));

        // 处理不同的授权范围
        return DelegationRoleHandler.handleDelegationRoles(delegationMap, existingRoles, roleMapper, roleService);
    }
}
