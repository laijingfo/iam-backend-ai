package com.lenovo.entity;

import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lenovo.util.JacksonUtil;
import lombok.Data;

import com.baomidou.mybatisplus.annotation.TableField;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@TableName(value = "user_access_review")
@Data
public class UserAccessReview
{
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("cmdb_id")
    private String cmdbId;
    @TableField("uar_id")
    private String uarId;
    @TableField("delete_flag")
    private Boolean deleteFlag;
    @TableField("leit_system_id")
    private String leitSystemId;
    @TableField("leit_system_name")
    private String leitSystemName;

    @TableField("sequence_number")
    private String sequenceNumber;
    @TableField("it_code_of_user")
    private String itCodeOfUser;
    @TableField("email")
    private String email;
    @TableField("user_name")
    private String userName;
    @TableField("user_real_name")
    private String userRealName;
    @TableField("country")
    private String country;
    @TableField("department")
    private String department;
    @TableField("app_name")
    private String appName;
    @TableField("user_id")
    private String userId;
    @TableField("user_id_valid_to")
    private LocalDate userIdValidTo;
    @TableField("authorization_technical_name")
    private String authorizationTechnicalName;
    @TableField("authorization_description")
    private String authorizationDescription;
    @TableField("authorization_valid_to")
    private LocalDate authorizationValidTo;
    @TableField("access_label")
    private String accessLabel;
    @TableField("first_line_manager")
    private String firstLineManager;
    @TableField("second_line_manager")
    private String secondLineManager;
    @TableField("it_code_of_bpo")
    private String itCodeOfBpo;

    @TableField("bpo_review_decision")
    private String bpoReviewDecision;
    @TableField("user_id_lock")
    private String userIdLock;
    @TableField("access_review_decision")
    private String accessReviewDecision;
    @TableField("audit_trail")
    private String auditTrail;

    @TableField("system_role_id")
    private String systemRoleId;

    @TableField("system_role")
    private String systemRole;

    @TableField("role_description")
    private String roleDescription;

    @TableField("bpo_review_status")
    private String bpoReviewStatus;
    @TableField("line_manager_review_status")
    private String lineManagerReviewStatus;

    @TableField("line_managers_review_decision")
    private String lineManagersReviewDecision;

    @TableField("final_review_decision")
    private String finalReviewDecision;

    @TableField("final_review_time")
    private LocalDate finalReviewTime;

    @TableField("final_review_result_reason")
    private String finalReviewResultReason;

    @TableField("line_manager")
    private String lineManager;

    @TableField("bpo")
    private String  bpo;
    @TableField("role_valid_to")
    private String roleValidTo;

    // 新增字段
    @TableField("overall_send_status")
    private String overallSendStatus;

    @TableField("current_round")
    private Integer currentRound;

    @TableField("uuid")
    private String uuid;
    @TableField("bpo_email")
    private String bpoEmail;

    /** NA policy CoC snapshot for the reviewed user: Y or N. */
    @TableField("user_coc_type")
    private String userCocType;

    /** NA policy CoC snapshot for a single BPO: Y or N. */
    @TableField("bpo_coc_type")
    private String bpoCocType;

    @TableField("line_manager_email")
    private String lineManagerEmail;

    @TableField("app_bpo")
    private String appBpo;
    @TableField("app_bpo_email")
    private String appBpoEmail;
    @TableField("backup_bpo")
    private String backupBpo;
    @TableField("backup_bpo_email")
    private String backupBpoEmail;

    /**lm决策的操作人*/
    @TableField("line_manager_review_itcode")
    private String lineManagerReviewItcode;
    /**lm决策的时间*/
    @TableField("line_manager_review_time")
    private LocalDate lineManagerReviewTime;
    /**bpo决策的操作人*/
    @TableField("bpo_review_itcode")
    private String bpoReviewItcode;
    /**bpo决策的时间*/
    @TableField("bpo_review_time")
    private LocalDate bpoReviewTime;
    /**修改bpo信息的操作人*/
    @TableField("bpo_update_by")
    private String bpoUpdateBy;
    /**修改bpo信息的操作时间*/
    @TableField("bpo_update_time")
    private LocalDateTime bpoUpdateTime;

    /** TIP: 用于标识对应的 BPO 人员是否属于以下管理层级：CEO, EVP, SVP, VP, ED
     若人员属于上述任一层级，则标记为 1；否则标记为 0
     若存在多人，则使用分号（; ）分隔
      */
    @JSONField(name = "line_manager_band_ed_flag")
    private String lineManagerBandEdFlag;

    @TableField("bpo_band_ed_flag")
    private String bpoBandEdFlag;

    @TableField("backup_bpo_band_ed_flag")
    private String  backupBpoBandEdFlag;
    @TableField("app_bpo_band_ed_flag")
    private String  appBpoBandEdFlag;

    @TableField("line_manager_level_code")
    private String  lineManagerLevelCode;


    /** 部门  */
    @TableField("dept")
    private String dept;

    /** 异常工单号 */
    @TableField("exception_ticket_no")
    private String exceptionTicketNo;

    /** 异常处理结果 */
    @TableField("exception_result_decision")
    private String exceptionResultDecision;

    /** 异常处理原因 */
    @TableField("exception_reason")
    private String exceptionReason;

    /** 异常处理人 */
    @TableField("exception_adjusted_by")
    private String exceptionAdjustedBy;

    /** 异常处理时间 */
    @TableField("exception_adjusted_time")
    private LocalDate exceptionAdjustedTime;

    // 非数据库字段，用于前端显示 - 修改为直接返回 UseAccessReviewEmailSend 实体类
    @TableField(exist = false)
    private List<UseAccessReviewEmailSend> lineManagerEmailStatus;

    @TableField(exist = false)
    private List<UseAccessReviewEmailSend> bpoEmailStatus;

    @TableField(exist = false)
    private String strLineManagerEmailStatus;

    @TableField(exist = false)
    private String strBpoEmailStatus;

    /**LM职级
     * 传入code：1234
     * 响应orig：ED/VP/SVP/EVP/CEO
     * */
    @TableField(exist = false)
    private String lineManagerLevel;

    /**应用描述*/
    @TableField(exist = false)
    private String appDescription;

    /**职务头衔*/
    @TableField(exist = false)
    private String businessTitle;

    /**办公地址*/
    @TableField(exist = false)
    private String businessAddress;

    @TableField(exist = false)
    private String lineManagerRealName;


    @TableField(exist = false)
    private String lineManagerCountry;


    @TableField(exist = false)
    private String lineManagerDept;


    @TableField(exist = false)
    private String lineManagerBusinessTitle;


    @TableField(exist = false)
    private String lineManagerBusinessAddress;

    @TableField(exist = false)
    private String ccEmail;



    // 构造函数，初始化UUID
    public UserAccessReview() {
        this.uuid = UUID.randomUUID().toString();
        this.overallSendStatus = "Pending";
        this.currentRound = 0;
    }

    public void setBpoReviewDecisionParam(String decision) {
        this.bpoReviewDecision = getDecisionParam( decision );
    }
    public void setLineManagersReviewDecisionParam(String decision) {
        this.lineManagersReviewDecision = getDecisionParam( decision );
    }

    public void setExceptionResultDecisionParam(String decision) {
        this.exceptionResultDecision = getDecisionParam( decision );
    }


    public void setAccessLabel(String accessLabel) {
        // 如果accessLabel = "LEIT_empty" 返回 null
        if ("LEIT_empty".equals(accessLabel)) {
            this.accessLabel = null;
        } else {
            this.accessLabel = accessLabel;
        }
    }

    public String getDecisionParam(String decision) {
        // 获取设置值
        if (decision != null) {
            decision = decision.toLowerCase();
        }
        // 对中文描述进行转换
        if ("保留".equals(decision)) {
            return "keep";
        } else if ("移除".equals(decision)) {
            return "remove";
        } else {
            return decision;
        }
    }
}
