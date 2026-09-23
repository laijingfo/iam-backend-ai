package com.lenovo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lenovo.entity.ApplicationAccessLink;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ApplicationAccessLinkMapper extends BaseMapper<ApplicationAccessLink> {

    List<ApplicationAccessLink> queryBySearchKey(@Param("searchKey") String searchKey);
}
