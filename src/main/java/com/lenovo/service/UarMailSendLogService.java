// UarMailSendLogService.java
package com.lenovo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lenovo.bean.UarMailSendLogBean;
import com.lenovo.entity.UarMailSendLog;

public interface UarMailSendLogService extends IService<UarMailSendLog> {
    Page<UarMailSendLog> query(UarMailSendLogBean bean, Long page, Long size);
}