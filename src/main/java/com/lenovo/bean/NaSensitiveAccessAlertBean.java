package com.lenovo.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/** NA敏感权限告警列表展示数据。 */
@Data
public class NaSensitiveAccessAlertBean {
    private Long id;
    private String cmdbId;
    private String appName;
    private String userItCode;
    private String userCocType;
    private String systemRole;
    private String roleDescription;
    private String roleClassification;
    private String alertStatus;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate alertTriggerTime;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate resolvedTime;

    private String suppressedBy;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate suppressedTime;

    private String suppressedReason;

    /** 告警抑制申请人 */
    private String suppressedRequestedBy;
}
