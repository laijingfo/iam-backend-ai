package com.lenovo.bean;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UarAlertBpoBean {
    private String cmdbId;
    private String appName;
    private Integer alertType;
    private String alertTypeStr;
    private String systemRole;
    private String bpo;

    private String operationOwner; //运维负责人
    private String operationFocal; //运维Focal
    private String operationOwnerDomain; //运维负责人域
    private String operationOwnerTower; //运维负责人tower
}
