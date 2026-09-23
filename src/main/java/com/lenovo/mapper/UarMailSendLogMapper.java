// UarMailSendLogMapper.java
package com.lenovo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.UarMailSendLogBean;
import com.lenovo.entity.UarMailSendLog;
import org.apache.ibatis.annotations.Param;

public interface UarMailSendLogMapper extends BaseMapper<UarMailSendLog> {
    Page<UarMailSendLog> query(Page p, @Param("bean") UarMailSendLogBean bean);
}