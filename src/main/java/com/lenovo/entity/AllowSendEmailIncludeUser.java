package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import com.baomidou.mybatisplus.annotation.TableField;
import java.time.LocalDateTime;

/**
 * @Description TODO 发送用户邮件处理好的允许发送的邮件信息
 * @ClassName AllowSendEmailIncludeUser
 * @Author wangfenglong
 * @Date 2026/3/13 15:25
 **/
@Data
@TableName("allow_send_email_include_user")
public class AllowSendEmailIncludeUser
{
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("it_code_of_user")
    private String itCodeOfUser;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
