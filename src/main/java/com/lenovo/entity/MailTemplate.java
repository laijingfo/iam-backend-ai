package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import com.lenovo.bean.CommonEmailBean;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * @TableName mail_template
 */
@TableName(value = "mail_template")
@Data
public class MailTemplate implements Serializable {
    /**
     *
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     *
     */
    private String title;

    /**
     *
     */
    private String bannerImg;

    /**
     *
     */
    private String content;

    /**
     *
     */
    private String period;

    /**
     *
     */
    private String receivers;

    /**
     *
     */
    private String cc;

    /**
     *
     */
    private String sender;

    /**
     *
     */
    private Boolean isDraft;

    /**
     *
     */
    private Integer activeStatus;

    @TableField(exist = false)
    private List<CaptureSourceFrom> sourceFroms;

    /**
     *
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     *
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime lastSendTime;

    /**
     *
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    /**
     *
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;

    /**
     *
     */
    private String owner;

    /**
     *
     */
    private String repeatString;

    /**
     *
     */
    private String signatures;

    /**
     *
     */
    private Integer workspaceId;

    /**
     * 邮件收件人选项
     */
    private Integer recipient;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    public CommonEmailBean toEmailBean() {
        CommonEmailBean commonEmailBean = new CommonEmailBean();
        commonEmailBean.setTitle(this.title);
        commonEmailBean.setBannerImg(this.bannerImg);
        commonEmailBean.setReportSubject(this.content);
        commonEmailBean.setReceivers(this.receivers);
        commonEmailBean.setCc(this.cc);
        commonEmailBean.setSender(this.sender);
        commonEmailBean.setSignatures(this.signatures);
        commonEmailBean.setWorkspaceId(this.workspaceId);
        commonEmailBean.setWorkspaceId(this.workspaceId);
        return commonEmailBean;
    }
}