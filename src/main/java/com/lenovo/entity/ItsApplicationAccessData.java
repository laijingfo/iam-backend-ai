package com.lenovo.entity;

import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.*;
import com.lenovo.security.utils.StringUtils;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * @TableName risk_management_original
 */
@TableName(value = "its_application_access_data")
@Data
public class ItsApplicationAccessData implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private String id;

    @JSONField(name = "cmdb_id")
    private String cmdbId;

    @JSONField(name = "Application_Name")
    private String applicationName;

    @JSONField(name = "Application")
    private String application;

    @JSONField(name = "LEIT_auth_access_label")
    private String accessLabel;

    @JSONField(name = "User_ID")
    private String userId;

    @JSONField(name = "User_IT_Code")
    private String userItCode;

    @JSONField(name = "Email")
    private String email;

    @JSONField(name = "User_Name")
    private String userName;

    @JSONField(name = "User_Real_Name")
    private String userRealName;

    @JSONField(name = "User_ID_Valid_To")
    private Date userIdValidTo;

    @JSONField(name = "System_Role_ID")
    private String systemRoleId;

    @JSONField(name = "System_Role")
    private String systemRole;

    @JSONField(name = "LEIT_auth_classification_label")
    private String systemRoleType;

    @JSONField(name = "Role_Description")
    private String roleDescription;

    @JSONField(name = "Role_Valid_To")
    private Date roleValidTo;

    @JSONField(name = "LEIT_System_ID")
    private String leitSystemId;

    @JSONField(name = "LEIT_system_name")
    private String leitSystemName;

    @JSONField(name = "Country")
    private String country;

    @JSONField(name = "Department")
    private String department;

    @JSONField(name = "Line_Manager")
    private String lineManager;

    @JSONField(name = "2nd_Line_Manager")
    private String secondLineManager;

    @JSONField(name = "Line_Manager_Email")
    private String lineManagerEmail;

    @JSONField(name = "BPO")
    private String bpo;

    @JSONField(name = "BPO_Email")
    private String bpoEmail;


    @JSONField(name = "backup_BPO")
    private String backupBpo;

    @JSONField(name = "backup_BPO_Email")
    private String backupBpoEmail;

    @JSONField(name = "App_BPO")
    private String appBpo;

    @JSONField(name = "App_BPO_Email")
    private String appBpoEmail;

    @JSONField(name = "Line_Manager_band_ed_flag")
    private String lineManagerBandEdFlag;

    @JSONField(name = "bpo_band_ed_flag")
    private String bpoBandEdFlag;

    @JSONField(name = "backup_bpo_band_ed_flag")
    private String  backupBpoBandEdFlag;
    @JSONField(name = "app_bpo_band_ed_flag")
    private String  appBpoBandEdFlag;
    @JSONField(name = "apply_form_role_state")
    private String  applyFormRoleState;




    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private String createBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private String updateBy;

    private static final long serialVersionUID = 1L ;


    /**
     * 时间戳转日期格式处理
     */
    public void setUserIdValidTo(String userIdValidTo) {
        if (StringUtils.isBlank(userIdValidTo)) return;
        long timestamp = (long) Double.parseDouble(userIdValidTo);
        this.userIdValidTo = new Date(timestamp * 1000);
    }

    /**
     * 时间戳转日期格式处理
     * 上游传入的是时间戳 实体用String接受了，数据库需要date类型
     */
    public void setRoleValidTo(String roleValidTo) {
        if (StringUtils.isBlank(roleValidTo)) return;
        long timestamp = (long) Double.parseDouble(roleValidTo);
        this.roleValidTo = new Date(timestamp * 1000);
    }


}
