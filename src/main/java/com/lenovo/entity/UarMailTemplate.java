// UarMailTemplate.java
package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("uar_mail_template")
public class UarMailTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String version;
    private String tag;
    private String content;
//    private Date createDate;
    private String theme;
    private String banner;
    private String subject;
    private String toSomeone;
    private Date dueDate;
    private String operator;       // 操作人
    private Date operationDate;    // 操作时间

}