package com.lenovo.bean;

import com.lenovo.security.utils.StringUtils;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


@Data
public class UarCycleMaintenanceBean {

    private String application;
    private String applicationId;
    private String cycleDataFlag;
    private String appUarPlatformStatus;
    private String uarProcessor;
    private String uarCycleStartMonth;
    private String uarCycleEndMonth;
    private LocalDate operatorDate;
    private String operatorName;
    private String operationOwner;
    private String applicationItOwner;
    private String decommissionReason;
    private String confirmationDate;
    private String modifiedBy;
    private LocalDate modificationDate;
    private List<String> dataRange;

    private String appStatus;
    private String onlineFlag;
    private String operationFocal;
    private String dataSource;

    private Integer page;
    private Integer size;

    private String language = "CN";
    private String source = "0";

    /** 初始值 */
    public UarCycleMaintenanceBean() {
        this.page = 1;
        this.size = 10;
    }
}
