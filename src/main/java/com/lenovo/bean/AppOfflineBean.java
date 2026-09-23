package com.lenovo.bean;

import lombok.Data;

import java.util.List;

@Data
public class AppOfflineBean {
    private List<String> applications;
    private String applicationOwner;
    private String decommissionReason;
}
