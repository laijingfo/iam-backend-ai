package com.lenovo.service;

import com.lenovo.bean.AdvancedSearchBean;
import com.lenovo.dto.ApplicationOperationFocalRequest;
import com.lenovo.dto.ApplicationUarProcessorRequest;

import java.util.List;
import java.util.Map;

public interface ItsApplicationService {
    /**
     * 同步上游[应用]数据到本系统
     */
//    void syncSplunkApplicationData();

    /**
     * 更新本地应用[数据准备状态]
     */
    void syncLocalApplicationReadyData();

    List<AdvancedSearchBean> getApplicationMenu(String type, List<String> dataRange);

    int updateUarProcessor(ApplicationUarProcessorRequest bean);

    int updateOperationFocal(ApplicationOperationFocalRequest bean);

    List<AdvancedSearchBean> getFocal(List<String> dataRange);

    List<AdvancedSearchBean> getSelectorOptions(String scene, String field, String currentDataCycle);
}
