package com.lenovo.bean;

import lombok.Data;

import java.util.List;

/**
 * Query parameters for the permission distribution dashboard.
 */
@Data
public class PermissionDistributionSearchBean {
    private String currentDataCycle;
    private Integer isCurrentCycle;
    private String cmdbId;
    private String appName;
    private List<String> dataRange;
    private String sortField;
    private String sortOrder;
    private Integer page = 1;
    private Integer size = 10;
}
