package com.lenovo.entity;

import com.lenovo.bean.UseAccessReviewBean;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class BatchOperationRequest {
    private String type;
    private String isALL;
    private List<String> sequenceNumber;
    private Conditions filters;

    // 实用方法
    public boolean isKeep() {
        return "keep".equalsIgnoreCase(type);
    }

    public boolean isRemove() {
        return "remove".equalsIgnoreCase(type);
    }

    public boolean isAll() {
        return "true".equalsIgnoreCase(isALL);
    }

    @Data
    public class Conditions {
        private String cmdbId;
        private String appName;
        private String itCodeOfUser;
        private String systemRole;
        private String accessLabel;
        private String lineManager;
        private String lineManagerReviewStatus;
        private String lineManagersReviewDecision;
        private String bpo;
        private String bpoReviewDecision;
        private String userCocType;
        private String bpoCocType;
        private String status;
        private List<String> dataRange;
        private Map<String, List<String>> dataRangeMap;
    }
}
