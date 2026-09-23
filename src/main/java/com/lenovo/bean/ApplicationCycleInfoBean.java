package com.lenovo.bean;

import com.lenovo.security.utils.StringUtils;
import lombok.Data;
import com.lenovo.constant.AccessReviewScope;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Data
public class ApplicationCycleInfoBean {
    /** ID */
    private String applicationId;
    /** 应用名称 */
    private String application;
    /** UAR平台ID */
    private String uarId;
    /** UAR平台名称 */
    private String uarName;
    /** 权限审核范围 (ALL_ACCESS: 全部权限, SENSITIVE_ACCESS: 仅敏感权限) */
    private AccessReviewScope accessReviewScope;
    /** 数据准备状态 */
    private String cycleDataFlag;
    /** UAR平台状态 */
    private String appUarPlatformStatus;
    /** 上线状态 */
    private Boolean onlineFlag;
    /** 上线状态字符串 */
    private String onlineFlagStr;
    /** 上线时间 */
    private LocalDate onlineTime;
    /** 下线状态 */
    private LocalDate offlineTime;
    /** 应用下线原因 */
    private String decommissionReason;
    /** UAR周期开始月份 */
    private LocalDate uarCycleStartMonth;
    /** UAR周期结束月份 */
    private LocalDate uarCycleEndMonth;
    /** 操作时间 */
    private LocalDate operatorDate;
    /** 操作人 */
    private String operatorName;
    /** 变更时间 */
    private LocalDate modificationDate;
    /** 变更时间 */
    private String modifiedBy;
    /** 确认时间 */
    private LocalDate confirmationDate;

    private List<String> uarProcessor;

    private String uarProcessorStr;
    private String uarProcessorEmail;
    /** 应用IT Owner */
    private String applicationItOwner;
    /** 运维Owner */
    private String operationOwner;
    /** 运维focal */
    private String operationFocal;
    /** 应用状态 上游数据 */
    private String appStatus;
    /** 数据来源 */
    private String dataSource;
    /** Access申请链接 */
    private String accessLink;

    private void setUarProcessor(String uarProcessor) {
        if (StringUtils.isBlank(uarProcessor))
            this.uarProcessor = Collections.emptyList(); // 返回前端，不可变集合
        else
            this.uarProcessor =  Arrays.asList(uarProcessor.split(","));
    }




}
