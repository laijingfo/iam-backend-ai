package com.lenovo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lenovo.entity.ItsApplicationAccessData;

import java.util.List;

/**
* @author mercury
* @description 针对表【its_application_access_data】的数据库操作Mapper
* @createDate 2024-10-28 11:38:48
* @Entity com.lenovo.entity.ItsApplicationAccessData
*/
public interface ItsApplicationAccessDataServiceMapper extends BaseMapper<ItsApplicationAccessData> {

    long countByCmdbId(String cmdbId);


    long batchInsert(List<ItsApplicationAccessData> list);

    void clearTable();

    void analyzeTable();
}




