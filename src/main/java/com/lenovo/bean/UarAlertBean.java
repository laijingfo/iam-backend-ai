package com.lenovo.bean;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UarAlertBean {
    private String id;
    private String sequenceNumber;
    private String cmdbId;
    private String appName;
    private Integer alertType;
    private String alertTypeStr;
    private String remark;
    private String alertHandleStatus;
    private String alertHandleStatusStr;
    private String itCodeOfUser;
    private String userId;
    private String userName;
    private String systemRole;
    private String roleDescription;
    private String lineManager;
    private String successionManager;
    private String bpo;
    private String userCocType;
    private String bpoCocType;
    private String managerOfBpo;
    private String createBy;
    private LocalDate createTime;
    private String updateBy;
    private LocalDate updateTime;

    //2026-3-4 用于给BPO的itCode失效发送邮件
    private String totalAlertCmdb;//获取Bpo的itcode失效的应用数量
    private String operationOwner; //运维负责人
    private String operationFocal; //运维Focal
    private String operationOwnerDomain; //运维负责人域
    private String operationOwnerTower; //运维负责人tower
}
