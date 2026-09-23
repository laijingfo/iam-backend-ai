package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Description TODO 存储最终审核是移除的用户数据
 * @ClassName AllowSendEmailIncludeFinalRemoveUser
 * @Author wangfenglong
 * @Date 2026/5/9 15:10
 **/
@Data
@TableName("allow_send_email_include_final_remove_user")
public class AllowSendEmailIncludeFinalRemoveUser
{
    /**
     * 主键ID 自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户邮箱IT编码
     * 对应字段：it_code_of_user
     */
    @TableField("it_code_of_user")
    private String itCodeOfUser;

    /**
     * 用户邮箱
     * 对应字段：it_code_of_user_email
     */
    @TableField("it_code_of_user_email")
    private String itCodeOfUserEmail;


    /**
     * 抄送
     * 对应字段：ccs_LineManager
     */
    @TableField("ccs_line_manager")
    private String ccsLineManager;


    /**
     * 当前用户级别
     * 对应字段：management_level
     */
    @TableField("management_level")
    private String managementLevel;

    /**
     * 当前易于理解的用户级别
     * 对应字段：level
     */
    @TableField("level")
    private String level;

    /**
     * 发送标识
     * 对应字段：send_flag
     */
    @TableField("send_flag")
    private String sendFlag;

    /**
     * 发送时间
     * 对应字段：send_time
     */
    @TableField("send_time")
    private LocalDateTime sendTime;

    /**
     * 创建时间
     * 对应字段：create_time
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     * 对应字段：update_time
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
