package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Description TODO
 * @ClassName AllowSendEmailForLm
 * @Author wangfenglong
 * @Date 2026/6/3 14:40
 **/
@Data
@TableName("allow_send_email_for_lm")
public class AllowSendEmailForLm
{
    /**
     * 主键自增 bigserial
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 唯一uuid
     */
    private String uuid;

    /**
     * 流水号
     */
    private String sequenceNumber;

    /**
     * 用户it编码
     */
    private String itCodeOfUser;

    /**
     * 用户姓名
     */
    private String userName;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 部门
     */
    private String department;

    /**
     * UAR单号
     */
    private String uarId;

    /**
     * CMDB编号
     */
    private String cmdbId;

    /**
     * 直属经理姓名
     */
    private String lineManager;

    /**
     * 直属经理邮箱 text
     */
    private String lineManagerEmail;

    /**
     * 抄送邮箱（逗号拼接）text
     */
    private String ccEmail;

    /**
     * 直属经理审核状态 text
     */
    private String lineManagerReviewStatus;

    /**
     * 职级编辑标识 text
     */
    private String lineManagerBandEdFlag;

    /**
     * 经理职级编码 varchar(255)
     */
    private String lineManagerLevelCode;

    /**
     * 创建时间 默认当前时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间 默认当前时间
     */
    private LocalDateTime updateTime;
}
