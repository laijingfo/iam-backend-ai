package com.lenovo.bean;

import lombok.Data;

import java.time.LocalDate;

@Data
public class DashboardUARBean {

    private Long id;
    private String cmdbId;
    private String uarId;
    private String leitSystemId;
    private String leitSystemName;
    private String sequenceNumber;
    private String itCodeOfUser;
    private String userBand;
    private String adValid;
    private String email;
    private String userName;
    private String userRealName;
    private String country;
    private String department;
    private String appName;
    private String userId;
    private LocalDate userIdValidTo;
    private String accessLabel;
    private String firstLineManager;
    private String secondLineManager;
    private String itCodeOfBpo;
    private String bpoReviewDecision;
    private String accessReviewDecision;
    private String systemRole;
    private String roleDescription;
    private String bpoReviewStatus;
    private String lineManagerDept;
    private String lineManagerReviewStatus;
    private String lineManagersReviewDecision;
    private String finalReviewDecision;
    private LocalDate finalReviewTime;
    private String lineManager;
    private String bpo;
    private String roleValidTo;
    private String uuid;
    private String bpoEmail;
    private String userCocType;
    private String bpoCocType;
    private String lineManagerEmail;
    private String uarProcessor;

    private String bpoReviewItcode;
    private LocalDate bpoReviewTime;
    private String lineManagerReviewItcode;
    private LocalDate lineManagerReviewTime;

    private String appOperationOwner;
    private String appOperationFocal;
    private String operationOwnerDomain;
    private String operationOwnerTower;
    private String lineManagerLevelCode;

    public void setBpoReviewDecisionParam(String bpoReviewDecision) {
        // 对中文描述进行转换
        if ("保留".equals(bpoReviewDecision)) {
            this.bpoReviewDecision = "keep";
        } else if ("移除".equals(bpoReviewDecision)) {
            this.bpoReviewDecision = "remove";
        } else {
            this.bpoReviewDecision = bpoReviewDecision;
        }
    }

    public void setLineManagersReviewDecisionParam(String lineManagersReviewDecision) {
        // 对中文描述进行转换
        if ("保留".equals(lineManagersReviewDecision)) {
            this.lineManagersReviewDecision = "keep";
        } else if ("移除".equals(lineManagersReviewDecision)) {
            this.lineManagersReviewDecision = "remove";
        } else {
            this.lineManagersReviewDecision = lineManagersReviewDecision;
        }
    }


}
