package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.baomidou.mybatisplus.annotation.TableField;
import java.util.List;

@Data
@TableName(value = "user_access_review_temp")
public class UserAccessReviewTemp
{
    // 数据库中存在的新增字段
    //private String lineManagerEmail;
    //private String bpoEmail;
    //private String sendFlag;

    // 邮件状态集合，MyBatis 会自动映射
    @TableField(exist = false)
    private List<UseAccessReviewEmailSend> lineManagerEmailStatus;
    @TableField(exist = false)
    private List<UseAccessReviewEmailSend> bpoEmailStatus;

    @TableId(value = "uuid")
    private String uuid;

    @TableField("sequence_number")
    private String sequenceNumber;

    @TableField("it_code_of_user")
    private String itCodeOfUser;

    @TableField("cmdb_id")
    private String cmdbId;

    @TableField("app_name")
    private String appName;

    @TableField("line_manager")
    private String lineManager;

    @TableField("line_manager_review_status")
    private String lineManagerReviewStatus;

    @TableField("line_manager_email")
    private String lineManagerEmail;

    @TableField("bpo")
    private String bpo;

    @TableField("bpo_review_status")
    private String bpoReviewStatus;

    @TableField("bpo_email")
    private String bpoEmail;

    @TableField("send_flag")
    private String sendFlag;

    @TableField("user_name")
    private String userName;

    @TableField("department")
    private String department;

    @TableField("current_round")
    private Integer currentRound;

    @TableField("overall_send_status")
    private String overallSendStatus;

    @TableField("uar_id")
    private String uarId;

    @TableField("bpo_band_ed_flag")
    private String bpoBandEdFlag;

    @TableField("line_manager_band_ed_flag")
    private String lineManagerBandEdFlag;

    @TableField("system_role")
    private String systemRole;

    @TableField("role_description")
    private String roleDescription;
}