package com.lenovo.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class NaSensitiveAccessAlertRequest {
    private String cmdbId;
    private String appName;
    private String userItCode;
    private String userId;
    private String systemRoleId;
    private String systemRole;
    private String alertStatus;
    private LocalDateTime alertTriggerStart;
    private LocalDateTime alertTriggerEnd;
    private String sortField;
    private String sortOrder;
    private Integer page = 1;
    private Integer size = 10;
    private String language;
    private List<String> dataRange;
}
