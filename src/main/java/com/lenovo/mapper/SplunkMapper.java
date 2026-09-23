package com.lenovo.mapper;

import com.lenovo.entity.AdAccountFromSplunk;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface SplunkMapper {

    int deleteAdAccount();

    int saveAllAdAccountFromSplunk(List<AdAccountFromSplunk> list);

    int deleteApplication();

    int saveCmdbApplicationListFromSplunk(List<Map> list);

    List<String> getAllItcode();



}
