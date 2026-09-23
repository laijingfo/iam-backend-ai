package com.lenovo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lenovo.entity.ItsApplicationAccessData;

/**
 * @author mercury
 * @description 针对表【its_application_access_data】的数据库操作Service
 * @createDate 2024-10-28 11:38:48
 */
public interface ItsApplicationAccessDataService  {


    /**
     * 同步全量数据
     * @param start
     */
    void syncFullData(int start);
}
