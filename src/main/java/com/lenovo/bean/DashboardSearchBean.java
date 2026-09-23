package com.lenovo.bean;

import com.lenovo.security.utils.RoleUtils;
import lombok.Data;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Data
public class DashboardSearchBean {
    private String currentDataCycle;
    private Integer isCurrentCycle;
    private String cmdbId;
    private String appName;
    private String appOperationOwner;
    private String appOperationFocal;
    private String operationOwnerDomain;
    private String operationOwnerTower;

    /** 基础查询参数 */
    private List<String> dataRange;
    private String sortField;
    private String sortOrder;
    private Integer page;
    private Integer size;

    /** 初始值 */
    public DashboardSearchBean() {
        this.page = 1;
        this.size = 10;
    }
}
