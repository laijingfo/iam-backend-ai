package com.lenovo.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import com.baomidou.mybatisplus.annotation.TableField;
import java.time.LocalDateTime;

/**
 * @Description TODO 发送邮件处理好的允许发送的邮件信息
 * @Author wangfenglong
 * @Date 2026/1/9 13:40
 **/
@Data
@TableName("allow_send_email_include_lm_and_bpo")
public class AllowSendEmailIncludeLmAndBpo
{
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 业务唯一标识ID
     */
    @TableField("uuid")
    private String uuid;

    @TableField("sequence_number")
    private String sequenceNumber;

    /**
     * 用户IT编码
     */
    @TableField("it_code_of_user")
    private String itCodeOfUser;

    /**
     * 用户名
     */
    @TableField("user_name")
    private String userName;

    /**
     * 应用系统名称
     */
    @TableField("app_name")
    private String appName;

    @TableField("department")
    private String department;

    /**
     * 权限审批单ID
     */
    @TableField("uar_id")
    private String uarId;

    @TableField("cmdb_id")
    private String cmdbId;

    /**
     * 直线经理姓名
     */
    @TableField("line_manager")
    private String lineManager;

    /**
     * 直线经理邮箱
     */
    @TableField("line_manager_email")
    private String lineManagerEmail;

    /**
     * 直线经理审批状态:2=已审核,其他=待审核
     */
    @TableField("line_manager_review_status")
    private String lineManagerReviewStatus;

    /**
     * 直线经理发送标识:1=禁止发送,其他=允许发送
     */
    @TableField("line_manager_band_ed_flag")
    private String lineManagerBandEdFlag;

    /**
     * BPO负责人姓名
     */
    @TableField("bpo")
    private String bpo;

    /**
     * BPO负责人邮箱
     */
    @TableField("bpo_email")
    private String bpoEmail;

    /**
     * BPO审批状态:2=已审核,其他=待审核
     */
    @TableField("bpo_review_status")
    private String bpoReviewStatus;

    /**
     * BPO发送标识:1=禁止发送,;=多邮箱拆分,split=拆分完成,Error=拆分异常
     */
    @TableField("bpo_band_ed_flag")
    private String bpoBandEdFlag;

    @TableField("line_manager_level_code")
    private String  lineManagerLevelCode;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}
