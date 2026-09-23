package com.lenovo.bean;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.lenovo.constant.AccessReviewScope;

import java.time.LocalDate;

/**
 * UAR 周期配置实体
 */
@Data
public class UarCycleSettingBean {

    /** 周期ID (主键) */
    private String uarId;

    /** 周期名称 */
    private String uarName;

    /** 是否当前周期 (1:是, 0:否) */
    private Integer isCurrent;

    /** 默认周期开始日期 */
    private LocalDate defaultCycleStartDate;

    /** 默认周期结束日期 */
    private LocalDate defaultCycleEndDate;

    /** 权限审核范围 (ALL_ACCESS: 全部权限, SENSITIVE_ACCESS: 仅敏感权限) */
    private AccessReviewScope accessReviewScope;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDate createTime;

    /** 修改人 */
    private String updateBy;

    /** 修改时间 */
    private LocalDate updateTime;
}
