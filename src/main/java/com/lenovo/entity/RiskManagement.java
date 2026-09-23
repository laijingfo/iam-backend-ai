package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * @TableName risk_management
 */
@TableName(value = "risk_management")
@Data
public class RiskManagement implements Serializable {
    private Integer id;

    private String checkSequenceId;

    private LocalDateTime handingTime;

    private String complianceCategory;



    private Integer complianceControlId;

    private String complianceId;

    private Boolean complianceStatus;

    private String application;

    private String appOwner;

    private String riskUserId;

    private String riskUserAccountType;

    private String riskUser1stLevelManager;

    private String riskUser2ndLevelManager;

    private String riskAuthId;

    private String riskUserStatus;

    private String bpoApprover;

    private String ssrTicketId;

    private String appTicketSubmitter;

    private String appTicketApprover;

    private String appOwnerDomain;

    private String appOwnerT2Org;

    private String appOwnerT3Org;

    private String appOwnerT4Org;

    private String riskHandlingAssignee;

    private String riskHandlingAssigneeT2Org;

    private String riskHandlingAssigneeT3Org;

    private String riskHandlingAssigneeT4Org;

    private String orgLevel1;

    private String orgLevel2;

    private String orgLevel3;

    private String orgLevel4;

    private LocalDate occurredDate;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private Integer evidenceType;

    private String evidenceContent;

    private String comment;

    private Integer riskStatus;

    private String monthDate;

    private String handingName;

    private String appOwnerEmail;


    private static final long serialVersionUID = 1L;

    @TableField(exist = false)
    private ComplianceControl complianceControl;
}