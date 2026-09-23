package com.lenovo.constant;

/**
 * @Description: 主要放调用api的固定参数
 */
public class ApiParamsConstant {

    public static final String AD_ACCOUNT_FOR_ACTIVE_USER = "inputlookup shonly_ITSC_ITCode_Total.csv";

//    public static final String CMDB_APPLICATION = "inputlookup shonly_ITSC_cmdb_application.csv";

    public static final String CMDB_APPLICATION = "inputlookup shonly_ITSC_cmdb_application_20230630.csv";

    //全量数据
    public static final String YEARLY_ALLOCATION = "inputlookup shonly_ITSC_BG_Allocation_FY2223.csv";

    //splunk那边Budget的汇总数据，从上面的全量数据计算得来
//    public static final String YEARLY_ALLOCATION = "inputlookup shonly_ITSC_Budget_Allocation_FY2324.csv";

    public static final String BILLING_DATA = "inputlookup shonly_ITSC_Charging_FY2324Q1.csv";

    public static final String APP_TICKET_SUMMARY = "| inputlookup shonly_BASD_opex_app_tickets_summary.csv";

    public static final String OPEX_OTHER = "| inputlookup shonly_BASD_opex_Other_summary.csv";

    public static final String OPEX_SW = "| inputlookup shonly_BASD_opex_SW.csv";

    public static final String APP_NON_TICKET_SUMMARY = "| inputlookup  shonly_BASD_opex_app_non_tickets_summary.csv";

    public static final String NON_APP_SUMMARY = "| inputlookup shonly_BASD_opex_non_app_summary.csv";


    //splunk接口同步模式
    public static final String SYNC_EXEC_MODE = "oneshot";
    //splunk接口异步模式
    public static final String ASYNC_EXEC_MODE = "normal";

    public static final String SPLUNK_PATH = "/servicesNS/-/search/search/jobs";

    public static final String MONITORING_P1_P2_EXEC_MODE = "oneshot";

    public static final String SPLUNK_IAM_PATH = "/servicesNS/-/LENOVO-IAM/search/jobs";
    public static final String IAM_RISK_SEARCH = "savedsearch IAM_Compliance_Raw_To_AIOP_Daily_Report";
    public static final String UAR_SEARCH = "savedsearch report_uar_raw_data";


    // CMDB 中的应用基础信息
    public static final String CMDB_APPLICATION_BASE_INFO = "inputlookup uar_its_application_data.csv";

    // IAM 已接入的用户与权限数据(异步查询)
    public static final String IAM_ACCESS_DATA = "inputlookup shonly_uar_its_application_access_data.csv";
}
