package com.lenovo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.AdvancedSearchBean;
import com.lenovo.bean.BpoItCodeExpireEmailData;
import com.lenovo.bean.UarAlertBean;
import com.lenovo.bean.UarAlertBpoBean;
import com.lenovo.dto.MarkBpoInvalidRequest;
import com.lenovo.dto.UarAlertRequest;
import com.lenovo.entity.UarMailTemplate;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface AlertDashboardService {
    /**
     * 检测[ItCode]失效，插入告警数据
     * @return
     */
    int insertUarAlertData();

    int insertManualBpoInvalid(MarkBpoInvalidRequest dto);

    Page<UarAlertBean> getList(UarAlertRequest uarAlertRequest);

    /**
     * 一键处理处理全部告警
     * @return
     */
    int handle();

    /**
     * 处理userItCode失效
     */
    int handleUserItCodeAlert();

    /**
     * 处理line_manager ItCode 失效
     */
    int handleLineManagerItCodeAlert();

    /**
     * 处理BPO ItCode 失效
     */
    int handleBpoInvalidAlert();


    void handleExpiredBpoNewFunc(String itCode, List<BpoItCodeExpireEmailData> dataList, UarMailTemplate bpoTemplate, String batchNo,String sendFlag);

    List<UarAlertBean> getAlertsForExport(UarAlertRequest uarAlertRequest);

    Page<UarAlertBpoBean> getAlertBpoSummary(UarAlertRequest uarAlertRequest);

    List<UarAlertBean> getAllAlertBpoSummary(UarAlertRequest uarAlertRequest);

    List<AdvancedSearchBean> getAlertInvalidBpoList(UarAlertRequest uarAlertRequest);

}
