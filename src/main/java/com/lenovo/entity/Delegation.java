package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import com.baomidou.mybatisplus.annotation.TableField;
import java.time.LocalDate;

/**
 * @Description TODO 授权表
 * @param
 * @author wangfenglong
 * @date 2025/11/18 17:14
**/
@Data
@TableName(value = "delegation")
public class Delegation
{
    /**
     * 主键 ID（自增序列，对应 PostgreSQL 的 bigserial）
     */
    @TableId(type = IdType.AUTO) // 适配 PostgreSQL bigserial 自增
    private Long id;

    @TableField("delegator")
    private String delegator;

    @TableField("delegatee")
    private String delegatee;

    @TableField("delegation_scope")
    private String delegationScope; //授权范围

    @TableField("delegation_start_date")
    private LocalDate delegationStartDate;

    @TableField("delegation_end_date")
    private LocalDate delegationEndDate;

    @TableField("delegation_status")
    private String delegationStatus;

    @TableField("creation_date")
    private LocalDate creationDate;

    @TableField("reason_for_delegation")
    private String reasonForDelegation;

    @TableField("delete")
    private Short delete;

    @TableField("delegation_cmdb_id")
    private String delegationCmdbId;

    @TableField("update_by")
    private String updateBy;
}
