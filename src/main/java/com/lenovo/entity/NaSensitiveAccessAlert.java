package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("na_sensitive_access_alert")
public class NaSensitiveAccessAlert {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String cmdbId;
    private String appName;
    private String userItCode;
    private String userId;
    private String userCocType;
    private String systemRoleId;
    private String systemRole;
    private String roleDescription;
    private String roleClassification;
    private String alertStatus;
    private LocalDateTime alertTriggerTime;
    private LocalDateTime resolvedTime;
    private String suppressedBy;
    private LocalDateTime suppressedTime;
    private String suppressedReason;
    /** 告警抑制申请人 */
    private String suppressedRequestedBy;
    private LocalDateTime lastSendTime;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String operationOwner;
    @TableField(exist = false)
    private String operationFocal;
}
