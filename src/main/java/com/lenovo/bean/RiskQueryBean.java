package com.lenovo.bean;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@Data
public class RiskQueryBean {
    private String complianceId;

    private String category;

    private String appOwnerDomain;

    private String appOwnerT2Org;

    private String appOwnerT3Org;

    private String appOwnerT4Org;

    private String riskHandlingAssignee;

    private String riskHandlingAssigneeT2Org;

    private String riskHandlingAssigneeT3Org;

    private String riskHandlingAssigneeT4Org;

    private List<String> application;

    private Integer riskStatus;


    private String startDate;


    private String endDate;

}
