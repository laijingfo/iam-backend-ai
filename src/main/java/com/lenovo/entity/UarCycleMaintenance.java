package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import com.lenovo.constant.AccessReviewScope;


@Data
@TableName("uar_cycle_maintenance")
public class UarCycleMaintenance {

    /** ID */
    @TableId(type = IdType.AUTO)
    private Long id ;

    /** 应用ID */
    private String cmdbId ;

    /** 周期ID手动填入 */
    private String uarId ;

    /** 周期名称 */
    private String uarName ;

    /** UAR平台状态: Null:空,Active:启用,Inactive:停用,Completed:已完成 */
    private String appUarPlatformStatus ;

    /** UAR周期开始日期 */
    private LocalDate uarCycleStartDate ;

    /** UAR周期结束日期 */
    private LocalDate uarCycleEndDate ;

    /** 权限审核范围 (ALL_ACCESS: 全部权限, SENSITIVE_ACCESS: 仅敏感权限) */
    private AccessReviewScope accessReviewScope;


    /** 操作时间 */
    private LocalDateTime operatorDate ;

    /** 操作人 */
    private String operatorName ;

    /** 变更时间 */
    private LocalDateTime modificationDate ;

    /** 变更人 */
    private String modifiedBy ;


    /** 确认时间 */
    private LocalDateTime confirmationDate ;

    /** 创建时间 */
    private LocalDateTime createTime ;

    /** 创建人 */
    private String createBy ;

    /** 更新时间 */
    private LocalDateTime updateTime ;

    /** 更新人 */
    private String updateBy ;

}
