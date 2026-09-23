package com.lenovo.entity;

import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "its_application_data")
public class ItsApplicationData {
    private static final long serialVersionUID = 1L ;

    @TableId(value = "id", type = IdType.AUTO)
    private String id;

    /** 上游数据 */
    // 应用ID
    @JSONField(name = "cmdb_id")
    private String cmdbId;
    // 应用名称
    @JSONField(name = "application_name")
    private String applicationName;
    // 应用描述
    @JSONField(name = "description")
    private String description;
    // 系统技术负责人
    @JSONField(name = "it_owner")
    private String applicationItOwner;
    // 运维负责人
    @JSONField(name = "operation_owner")
    private String operationOwner;
    @JSONField(name = "operation_focal")
    private String operationFocal;
    // 应用当前状态（如 Active / Planned / Decommissioned 等）
    @JSONField(name = "app_status")
    private String appStatus;
    // 应用认证状态（如 Registered / Certified / Invalid / BU Application 等）
    @JSONField(name = "certification_status")
    private String certificationStatus;
    // CMDB 中记录的应用信息最近更新时间，来自上游数据。
    @JSONField(name = "update_time")
    private LocalDateTime cmdbUpdateTime;
    // 应用在 CMDB 中标记为 Active 的时间（若为空，表示无记录）
    @JSONField(name = "active_time")
    private LocalDateTime cmdbActiveTime;
    // 应用所有者域
    @JSONField(name = "operation_owner_domain")
    private String operationOwnerDomain;
    // 应用所有者塔
    @JSONField(name = "operation_owner_tower")
    private String operationOwnerTower;
    // 应用是否NA敏感
    @JSONField(name = "sensitive_app")
    private String sensitiveApp;


    /** 本地系统数据 */
    // 数据准备状态(1:Ready / 0:Unready)
    private Boolean dataReadyFlag;
    // 数据删除状态(0/1)
    private Boolean deleteFlag;
    // 本地数据的上下线状态标识(0/1) 开启后才能走周期计划
    private Boolean onlineFlag;
    // 上线时间
    private LocalDateTime onlineTime;
    // 下线时间
    private LocalDateTime offlineTime;
    // 下线原因
    private String decommissionReason;
    // 权限相关:角色数据
    private List<String> uarProcessor;
    // 权限相关:itcode的邮箱
    private String uarProcessorEmail;



    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.UPDATE)
    private String updateBy;

    private Long syncBatchId;


    private LocalDateTime operatorDate;

    private String operatorName;


    // 上下线变更人
    private String modifiedBy;
    // 上下线变更时间
    private LocalDateTime modificationDate;
}
