package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @Description TODO 系统操作日志记录实体类
 * @ClassName SysActionLog
 * @Author wangfenglong
 * @Date 2025/11/12 10:06
 **/
@Data
@TableName(value = "sys_action_log")
public class SysActionLog implements Serializable
{
    /**
     * 主键 ID（自增序列，对应 PostgreSQL 的 bigserial）
     */
    @TableId(type = IdType.AUTO) // 适配 PostgreSQL bigserial 自增
    private Long id;

    /**
     * 操作用户名
     */
    @TableField("username")
    private String username;

    /**
     * 操作描述（如：切换系统、新增用户等）
     */
    @TableField("operation")
    @Size(max = 50, message = "操作描述不能超过 50 字符！")
    private String operation;

    /**
     * 调用的方法名（全类名+方法名）
     */
    @TableField("method") // 数据库字段为 "method"（关键字），需保持一致
    private String method;

    /**
     * 请求 URI（接口路径）
     */
    @TableField("request_uri")
    private String requestUri;

    /**
     * 请求参数（JSON 格式字符串）
     */
    @TableField("params")
    @Size(max = 5000, message = "请求参数长度不能超过 5000 字符！")
    private String params;

    /**
     * 操作耗时（毫秒）
     */
    @TableField("time") // 数据库字段为 "time"（关键字），需保持一致
    private Long time;

    /**
     * 操作状态（1-成功，0-失败，对应数据库 int2 类型）
     */
    @TableField("status")
    private Integer status;

    /**
     * 客户端 IP 地址
     */
    @TableField("ip")
    private String ip;

    /**
     * 客户端 User-Agent（浏览器/设备信息）
     */
    @TableField("user_agent")
    @Size(max = 5000, message = "客户端 User-Agent不能超过 3000 字符！")
    private String userAgent;

    /**
     * 操作时间（对应数据库 timestamp 类型）
     */
    @TableField("create_date")
    private LocalDateTime createDate; // 使用 LocalDateTime 适配 PostgreSQL timestamp，避免时区问题

    /**
     * 租户 ID（多租户隔离字段）
     */
    @TableField("tenant_id")
    private String tenantId;
}
