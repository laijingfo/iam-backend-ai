package com.lenovo.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lenovo.entity.LenovoUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 用户mapper
 */

@Mapper
public interface LenovoUserMapper extends BaseMapper<LenovoUser> {

    List<Map<String,String>> getDept();

    List<LenovoUser> getUserAndManagerInfo(
            @Param("itCodes") List<String> itCodes
    );

    /**
     * 备份数据，清空原表
     * 初始化None数据（用来命中is null的条件）
     */
    void backupTable();

    List<String> getAboveUser();

    void analyzeData();

    LenovoUser getUserInfoByItCode(String itCode);
}