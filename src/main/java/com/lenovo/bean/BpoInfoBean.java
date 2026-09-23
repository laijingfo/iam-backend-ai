package com.lenovo.bean;

import com.lenovo.security.utils.SecurityUtils;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BpoInfoBean {
    private String sequenceNumber;
    private String bpo;
    private String bpoEmail;

    private String bpoUpdateBy;
    private LocalDateTime bpoUpdateTime;

    public BpoInfoBean() {
        this.bpoUpdateTime = LocalDateTime.now();
        this.bpoUpdateBy = SecurityUtils.getCurrentUserId();
    }

}
