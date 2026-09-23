package com.lenovo.bean;

import lombok.Data;

@Data
public class RiskManagementStatisticsBean {
    // 全部
    private Integer all;
    // 待处理
    private Integer pendingProcessing;
    // 已完成
    private Integer completed;
}
