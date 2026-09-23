package com.lenovo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lenovo.entity.SysActionLog;

import java.util.List;


/**
 * @Description 系统操作日志服务
 * @param
 * @author wangfenglong
 * @date 2025/11/12 17:11
**/
public interface SysActionLogService extends IService<SysActionLog>
{
    //保存日志
    void addSysActionLog(SysActionLog sysActionLog);

    Page<SysActionLog> getActionLogByMethod(Long pageIndex, Long pageSize, List<String> methods);
}
