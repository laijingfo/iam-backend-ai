package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("use_access_review_email_send")
public class UseAccessReviewEmailSend {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String reviewUuid;
    private Integer sendRound;
    private LocalDateTime sendTime;
    private String sendStatus;
    private String recipientType;
    private String recipientEmail;
    private Long templateId;
    private String errorMessage;
    private String batchNo;
}