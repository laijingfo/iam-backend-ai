package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Description TODO bpo信息允许发送邮件
 * @ClassName AllowSendEmailForBpo
 * @Author wangfenglong
 * @Date 2026/6/3 14:42
 **/
@Data
@TableName("allow_send_email_for_bpo")
public class AllowSendEmailForBpo
{
    @TableId(type = IdType.AUTO)
    private Long id;

    private String uuid;

    private String sequenceNumber;

    private String itCodeOfUser;

    private String userName;

    private String appName;

    private String department;

    private String uarId;

    private String cmdbId;

    /** BPO姓名 */
    private String bpo;

    /** BPO邮箱 */
    private String bpoEmail;

    /** BPO审核状态 */
    private String bpoReviewStatus;

    /** BPO职级编辑标识 */
    private String bpoBandEdFlag;

    /** 抄送邮箱，逗号分隔 */
    private String ccEmail;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
