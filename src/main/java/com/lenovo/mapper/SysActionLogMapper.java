package com.lenovo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.entity.SysActionLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @Description TODO 系统操作日志
 * @ClassName SysActionLogMapper
 * @Author wangfenglong
 * @Date 2025/11/12 13:59
 **/
public interface SysActionLogMapper extends BaseMapper<SysActionLog>
{
    void addSysActionLog(@Param("SysActionLog")SysActionLog sysActionLog);

    Page<SysActionLog> getActionLogByMethod(
            Page<SysActionLog> page,
            @Param("methods") List<String> methods
    );
}
