package com.lenovo.bean;

import lombok.Data;

import java.util.Date;

@Data
public class UarMailTemplateBean {
    private Long id;
    private String version;
    private String tag;
    private String content;
    private Date createDate;
    private String theme;
    private String banner;
    private String subject;
    private String toSomeone; // 确保有这个字段
    private Date dueDate;
    private String operator;       // 操作人
    private Date operationDate;    // 操作时间

}