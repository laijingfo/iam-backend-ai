package com.lenovo.entity;


import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Description TODO 业务流程负责人自动邮件发送临时表实体类
 * @author wangfenglong
 * @date 2025/12/30 09:37
**/
@Data
@TableName("auto_mail_send_bpo_temp")
public class AutoMailSendBpoTemp
{
    /**
     * 主键自增
     */
    @TableId(type = IdType.AUTO) // 适配PostgreSQL的BIGSERIAL自增策略
    private Long id;

    /**
     * 直属经理itCode
     */
    @TableField("bpo")
    private String bpo;

    /**
     * 直属经理邮箱
     */
    @TableField("bpo_email")
    private String bpoEmail;

    /**
     * BPO发送标志
     */
    @TableField("bpo_band_ed_flag")
    private String bpoBandEdFlag;

    /**
     * 收件人邮箱
     */
    @TableField("to_email")
    private String toEmail;

    /**
     * userAccessReview的cmdbId
     */
    @TableField("cmdb_id")
    private String cmdbId;

    /**
     * 邮件标题
     */
    @TableField("subject")
    private String subject;

    /**
     * 邮件内容
     */
    @TableField("content") // TEXT类型指定
    private String content;

    /**
     * 应用个数
     */
    @TableField("app_name_number")
    private String appNameNumber;

    /**
     * 应用名称集合
     */
    @TableField("app_name_list")
    private String appNameList;

    /**
     * 用户个数
     */
    @TableField("it_code_user_number")
    private String itCodeUserNumber;

    /**
     * 用户名称集合
     */
    @TableField("it_code_user_name_list")
    private String itCodeUserNameList;

    /**
     * 审核条目个数
     */
    @TableField("review_number")
    private String reviewNumber;

    /**
     * 审核条目名称集合
     */
    @TableField("review_number_name_list")
    private String reviewNumberNameList;

    /**
     * 状态：0-待发送 1-发送中 2-发送成功 3-发送失败
     */
    @TableField("status")
    private Integer status = 0;

    /**
     * 处理节点标识（IP+端口）
     */
    @TableField("process_node")
    private String processNode;

    /**
     * 抢占时间
     */
    @TableField("process_time")
    private LocalDateTime processTime;

    /**
     * 重试次数
     */
    @TableField("retry_count")
    private Integer retryCount = 0;

    /**
     * 发送中超时时间（秒，默认5分钟）
     */
    @TableField("timeout")
    private Integer timeout = 300;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime = LocalDateTime.now();

    @Override
    public String toString()
    {
        return "AutoMailSendBpoTemp{" +
                "id=" + id +
                ", bpo='" + bpo + '\'' +
                ", bpoEmail='" + bpoEmail + '\'' +
                ", bpoBandEdFlag='" + bpoBandEdFlag + '\'' +
                ", toEmail='" + toEmail + '\'' +
                ", cmdbId='" + cmdbId + '\'' +
                ", subject='" + subject + '\'' +
                ", content='" + content + '\'' +
                ", appNameNumber='" + appNameNumber + '\'' +
                ", appNameList='" + appNameList + '\'' +
                ", itCodeUserNumber='" + itCodeUserNumber + '\'' +
                ", itCodeUserNameList='" + itCodeUserNameList + '\'' +
                ", reviewNumber='" + reviewNumber + '\'' +
                ", reviewNumberNameList='" + reviewNumberNameList + '\'' +
                ", status=" + status +
                ", processNode='" + processNode + '\'' +
                ", processTime=" + processTime +
                ", retryCount=" + retryCount +
                ", timeout=" + timeout +
                ", createTime=" + createTime +
                '}';
    }
}
