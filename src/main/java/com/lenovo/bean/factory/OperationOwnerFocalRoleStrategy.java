package com.lenovo.bean.factory;

import cn.hutool.core.collection.CollectionUtil;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.entity.ItsApplicationData;
import com.lenovo.entity.Role;
import com.lenovo.mapper.ItsApplicationDataMapper;
import com.lenovo.mapper.RoleMapper;
import com.lenovo.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @Description TODO OPERATION_OWNER_FOCAL策略：从its_application_data表获取角色
 * @ClassName OperationOwnerFocalRoleStrategy
 * @Author wangfenglong
 * @Date 2026/1/22 12:07
 **/
@Component
@RequiredArgsConstructor
public class OperationOwnerFocalRoleStrategy implements RoleRetrievalStrategy
{
    private final ItsApplicationDataMapper itsApplicationDataMapper;
    private final RoleMapper roleMapper;
    private final RoleService roleService;

    @Override
    public List<Role> retrieveRoles(String itcode, List<Role> existingRoles)
    {
        UseAccessReviewBean bean = new UseAccessReviewBean();
        bean.setItCodeOfUser(itcode);
        List<ItsApplicationData> uarList = itsApplicationDataMapper.getApplicationDataByOperationOwnerOrOperationFocal(bean);
        if(CollectionUtil.isEmpty(uarList))
        {
            return List.of();
        }

        // 拼接cmdbId
        String cmdbIdStr = uarList.stream().map(ItsApplicationData::getCmdbId).filter(Objects::nonNull).collect(Collectors.joining(","));

        // 创建或更新UAR_PROCESSER角色
        return OperationOwnerFocalRoleHandler.handleOperationOwnerFocalRole(existingRoles, cmdbIdStr, roleMapper, roleService);
    }
}
