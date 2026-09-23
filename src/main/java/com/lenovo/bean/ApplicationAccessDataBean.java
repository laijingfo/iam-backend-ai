package com.lenovo.bean;

import lombok.Data;

@Data
public class ApplicationAccessDataBean {
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


    public void setSystemRoleType(String systemRoleType) {
        // 如果systemRoleType = "LEIT_empty" 返回 null
        if ("LEIT_empty".equals(systemRoleType)) {
            this.systemRoleType = null;
        } else {
            this.systemRoleType = systemRoleType;
        }
    }

    public void setAccessLabel(String accessLabel) {
        // 如果accessLabel = "LEIT_empty" 返回 null
        if ("LEIT_empty".equals(accessLabel)) {
            this.accessLabel = null;
        } else {
            this.accessLabel = accessLabel;
        }
    }
}
