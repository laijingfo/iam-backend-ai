package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @Description TODO 发送邮件记录日志
 * @ClassName SendEmailActionLog
 * @Author wangfenglong
 * @Date 2026/1/20 15:53
 **/
@Data
@TableName(value = "send_email_action_log")
public class SendEmailActionLog implements Serializable
{
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("it_code")
    private String itCode;

    @TableField("operation")
    private String operation;

    @TableField("send_flag")
    private String sendFlag;

    @TableField("message")
    private String message;

    @TableField("stack_trace")
    private String stackTrace;

    @TableField("batch_no")
    private String batchNo;

    @TableField("create_date")
    private LocalDateTime createDate;
}
