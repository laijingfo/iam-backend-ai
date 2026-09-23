package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("uar_mail_send_log")
public class UarMailSendLog
{
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private String recipientEmails;
    private String ccEmails;
    private String subject;
    private String content;
    private String senderAlias;
    private Date sendTime;
    private String status;
    private String errorMessage;
    private String batchNo;
    private String sendFlag;
    private String recipientItCode;
    private String delegate;
    private String randomSequence;
    private String uuid;
}