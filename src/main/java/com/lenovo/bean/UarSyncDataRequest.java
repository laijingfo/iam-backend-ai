package com.lenovo.bean;

import lombok.Data;

@Data
public class UarSyncDataRequest {
    private String cmdbId;
    private String appName;
    private String itCodeOfUser;
    private String userRealName;
    private String systemRoleId;
    private String systemRole;
    private String systemRoleType;
    private String roleDescription;
    private String accessLabel;
    private String lineManager;
    private String lineManagerEmail;
    private String lineManagerLevelCode;
    private String bpo;
    private String bpoEmail;
    private String bpoLevelCode;

    private Integer page = 1;
    private Integer size = 10;
    private String sortField;
    private String sortOrder;
    private java.util.List<String> dataRange;

    private String language = "CN";
}
