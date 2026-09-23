package com.lenovo.bean;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;


@Data
public class DelegationBean
{
    private Long id;
    private String delegator; //授权人
    private String delegatee; //被委托人
    private String delegationScope;//委托范围
    private LocalDate delegationStartDate; //授权开始日期
    private LocalDate delegationEndDate; //授权结束日期
    private String delegationStatus; //授权状态
    private LocalDate creationDate; //授权创建日期
    private String reasonForDelegation; //授权原因
    private Short delete;
    private List<String> delegatorList;
    private String currentUserItCode;

    /*授权BPO需要可以选择授权出去的具体数据*/
    private String sequenceNumber;
    private String cmdbId;
    private String itCodeOfUser;
    private String appName;
    private String line_manager;
    private String bpo;
    private String delegationFlag;
    private String delegationCmdbId;
    private String roleFlag;
}
