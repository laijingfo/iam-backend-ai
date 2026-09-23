package com.lenovo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lenovo.bean.AppOfflineBean;
import com.lenovo.bean.UarCycleMaintenanceBean;
import com.lenovo.bean.UarCycleBean;
import com.lenovo.entity.ItsApplicationData;
import com.lenovo.entity.UarCycleMaintenance;
import com.lenovo.bean.ApplicationCycleInfoBean;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface UarCycleMaintenanceService extends IService<UarCycleMaintenance> {


    public Page<ApplicationCycleInfoBean> query(UarCycleMaintenanceBean uarCycleMaintenanceBean);

    List<ApplicationCycleInfoBean> queryFullData(UarCycleMaintenanceBean uarCycleMaintenanceBean);

    public Integer batchUpdateAppOnlineTime(UarCycleBean uarCycleBean);

    public Integer batchUpdateAppOfflineTime(UarCycleBean uarCycleBean);

    //批量上线和批量下线发送邮件服务
    CompletableFuture<List<ItsApplicationData>> batchSendEmailsAsyncByOnlineTimeAndOfflineTime(UarCycleBean uarCycleBean, String itCode,String sendFlag,String batchNo);

    //给选中应用的负责人发送开启本轮UAR周期的通知邮件
    UarCycleBean batchSendEmailsAsyncByInitUarCycleAndOfflineTime(UarCycleBean uarCycleBean);

    List<Map<String, Object>> uar_cycle_maintenance_distinct_list();

    List<Map<String, Object>> uar_cycle_maintenance_distinct_application();

    String batchInitUarCycle(UarCycleBean uarCycleBean);

    String batchCompleteCycle(UarCycleBean uarCycleBean);

}
