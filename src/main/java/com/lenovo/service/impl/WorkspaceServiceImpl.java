package com.lenovo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.entity.Workspace;
import com.lenovo.mapper.RoleMapper;
import com.lenovo.security.utils.StringUtils;
import com.lenovo.service.WorkspaceService;
import com.lenovo.mapper.WorkspaceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author mercury
 * @description 针对表【workspace】的数据库操作Service实现
 * @createDate 2024-08-27 10:55:58
 */
@Service
@RequiredArgsConstructor
public class WorkspaceServiceImpl extends ServiceImpl<WorkspaceMapper, Workspace>
        implements WorkspaceService {

    private final RoleMapper roleMapper;


    @Override
    public Page<Workspace> query(String creator, String user, Integer page, Integer size) {
        Page p = new Page(page, size);
        if (StringUtils.isNotEmpty(creator)) {
            return query().eq("creator", creator).page(p);
        } else if (StringUtils.isNotEmpty(user)) {
            return getBaseMapper().queryByUser(p, user);
        } else {
            return page(p);
        }
    }

    @Override
    public void delete(Integer id) {
        roleMapper.deleteByWorkspaceId(id);
        removeById(id);
    }
}




