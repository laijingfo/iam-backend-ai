package com.lenovo.entity;

import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@TableName("ad_tb_upp_nature_2")
@Data
public class LenovoUser {

    @TableId(value = "id", type = IdType.AUTO)
    private String id;
    /**
     * itCode /用户名
     */
    @JSONField(name = "user_name")
    private String userName;
    /**
     * 邮箱
     */
    @JSONField(name = "email")
    private String email;
    /**
     * 用户类型(内外部，其他)
     */
    @JSONField(name = "employee_type")
    private String employeeType;
    /**
     * 职级
     */
    @JSONField(name = "management_level")
    private String managementLevel;
    /**
     * 真实姓名
     */
    @JSONField(name = "real_name")
    private String realName;
    /**
     * 是否高管
     */
    private String bandFlag;
    /**
     * 上级
     */
    @JSONField(name = "manager1st")
    private String manager1st;
    /**
     * 国家
     */
    @JSONField(name = "country")

    private String country;
    /**
     * 业务单元
     */
    @JSONField(name = "BU")
    private String bu;
    /**
     * 部门
     */
    @JSONField(name = "lenovo_dept")
    private String lenovoDept;

    /**
     * 业务单元
     */
    @JSONField(name = "business_title")
    private String businessTitle;
    /**
     * 部门
     */
    @JSONField(name = "business_address")
    private String businessAddress;

    /**
     * 是否敏感地区
     */
    @JSONField(name = "coc_flag")
    private String cocFlag;

    /**
     * 一般存的是领导的职级
     * 如果需调整，注意查询方法
     */
    @TableField(exist = false)
    private String lineManagerLevelCode;

    /**
     * 领导的邮箱
     */
    @TableField(exist = false)
    private String lineManagerEmail;

    /**
     * 领导的band
     */
    @TableField(exist = false)
    private String lineManagerBandFlag;

    // 这里是查询时候使用，带出职级
    private void setLineManagerLevelCode(String managementLevel) {
        if (managementLevel == null) {
            return;
        }
        switch (managementLevel) {
            case "ED":
            case "Executive Director":
                this.lineManagerLevelCode = "2";
                break;
            case "VP":
            case "Vice President":
                this.lineManagerLevelCode = "3";
                break;
            case "SVP":
            case "Senior Vice President":
            case "EVP":
            case "Executive Vice President":
            case "CEO":
            case "Chief Executive Officer":
                this.lineManagerLevelCode = "4";
                break;
            default:
                this.lineManagerLevelCode = "1";
        }
    }


    public void setManagementLevel(String managementLevel) {
        if (managementLevel == null) {
            return;
        }
        this.managementLevel = managementLevel;
        if (bandFlag == null) {
            switch (managementLevel) {
                case "SVP":
                case "Senior Vice President":
                case "EVP":
                case "Executive Vice President":
                case "CEO":
                case "Chief Executive Officer":
                    this.bandFlag = "1";
                    break;
                default:
                    this.bandFlag = "0";
            }
        }
    }

}
