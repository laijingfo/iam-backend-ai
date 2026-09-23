package com.lenovo.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.entity.Workspace;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * @author mercury
 * @description 针对表【workspace】的数据库操作Mapper
 * @createDate 2024-08-27 10:55:58
 * @Entity com.lenovo.entity.Workspace
 */
public interface WorkspaceMapper extends BaseMapper<Workspace> {

    Page<Workspace> queryByUser(Page page, String user);
}




