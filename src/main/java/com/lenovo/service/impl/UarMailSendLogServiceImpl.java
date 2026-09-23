// UarMailSendLogServiceImpl.java
package com.lenovo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.bean.UarMailSendLogBean;
import com.lenovo.entity.UarMailSendLog;
import com.lenovo.mapper.UarMailSendLogMapper;
import com.lenovo.service.UarMailSendLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UarMailSendLogServiceImpl extends ServiceImpl<UarMailSendLogMapper, UarMailSendLog> implements UarMailSendLogService {

    @Override
    public Page<UarMailSendLog> query(UarMailSendLogBean bean, Long page, Long size) {
        Page p = new Page(page, size);
        return getBaseMapper().query(p, bean);
    }
}