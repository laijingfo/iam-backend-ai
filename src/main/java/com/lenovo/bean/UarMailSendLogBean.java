// UarMailSendLogBean.java
package com.lenovo.bean;

import lombok.Data;

import java.util.Date;

@Data
public class UarMailSendLogBean {
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
}