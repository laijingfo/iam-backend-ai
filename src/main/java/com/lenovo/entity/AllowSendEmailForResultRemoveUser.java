package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import com.baomidou.mybatisplus.annotation.TableField;
import java.time.LocalDateTime;

/**
 * @Description TODO 存储lm或者bpo审核结果是remove的用户数据 <每日怀念你>
 * @ClassName AllowSendEmailForResultRemoveUser
 * @Author wangfenglong
 * @Date 2026/6/25 15:09
 **/
@Data
@TableName("allow_send_email_for_result_remove_user")
public class AllowSendEmailForResultRemoveUser
{
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("it_code_of_user_email")
    private String itCodeOfUserEmail;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
