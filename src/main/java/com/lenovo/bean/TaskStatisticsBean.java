package com.lenovo.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TaskStatisticsBean {

    private TaskCount pendingProcessing;
    private TaskCount completed;

    @Data
    @AllArgsConstructor
    public static class TaskCount {
        private Integer total;
        private Integer lineManager;
        private Integer bpo;
    }
}
