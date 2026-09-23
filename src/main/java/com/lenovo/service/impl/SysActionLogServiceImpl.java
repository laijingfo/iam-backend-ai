package com.lenovo.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.entity.SysActionLog;
import com.lenovo.mapper.SysActionLogMapper;
import com.lenovo.service.SysActionLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @Description TODO 操作日志服务
 * @ClassName SysActionLogServiceImpl
 * @Author wangfenglong
 * @Date 2025/11/12 13:46
 **/
@Slf4j
@Service
public class SysActionLogServiceImpl extends ServiceImpl<SysActionLogMapper, SysActionLog> implements SysActionLogService
{
    /**
     * @Description TODO 保存操作日志
     * @param sysActionLog
     * @author wangfenglong
     * @date 2025/11/12 17:12
    **/
    @Override
    public void addSysActionLog(SysActionLog sysActionLog)
    {
        getBaseMapper().addSysActionLog(sysActionLog);
    }

    @Override
    public Page<SysActionLog> getActionLogByMethod(Long pageIndex, Long pageSize, List<String> methods) {
        Page<SysActionLog> page = Page.of(pageIndex, pageSize);
        return getBaseMapper().getActionLogByMethod(page, methods);
    }

    @Override
    public boolean saveBatch(Collection<SysActionLog> entityList, int batchSize) {
        return false;
    }

    @Override
    public boolean saveOrUpdateBatch(Collection<SysActionLog> entityList, int batchSize) {
        return false;
    }

    @Override
    public boolean updateBatchById(Collection<SysActionLog> entityList, int batchSize) {
        return false;
    }

    @Override
    public boolean saveOrUpdate(SysActionLog entity) {
        return false;
    }

    @Override
    public SysActionLog getOne(Wrapper<SysActionLog> queryWrapper, boolean throwEx) {
        return null;
    }

    @Override
    public Map<String, Object> getMap(Wrapper<SysActionLog> queryWrapper) {
        return Map.of();
    }

    @Override
    public <V> V getObj(Wrapper<SysActionLog> queryWrapper, Function<? super Object, V> mapper) {
        return null;
    }
}
