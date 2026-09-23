package com.lenovo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.entity.Workspace;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @author mercury
 * @description 针对表【workspace】的数据库操作Service
 * @createDate 2024-08-27 10:55:58
 */
public interface WorkspaceService extends IService<Workspace> {

    Page<Workspace> query(String creator, String user, Integer page, Integer size);

    void delete(Integer id);
}
