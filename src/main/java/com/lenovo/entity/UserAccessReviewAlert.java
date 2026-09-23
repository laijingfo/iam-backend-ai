package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName(value = "user_access_review_alert")
@Data
public class UserAccessReviewAlert {

    /**告警类型:
     * 失效
     *      10:user ItCode；
     *      11:line_manager ItCode；
     *      12:bpo ItCode；
     * 变更
     *      21:line_manager ；
     * 手动失效
     *      32:bpo 手动失效
     * */
    private Integer alertType;
    /**备注 */
    private String remark;

    /**
     *  记录数据创建时间和处理时间
     */
    private String createBy;
    private LocalDate createTime;
    private String updateBy;
    private LocalDate updateTime;
    /** 处理状态:
     * 待处理       Pending
     * 处理完成     Completed
     * */
    private String alertHandleStatus;

    /**
     *  下面字段全部来自 user_access_review 表
     *
     */

    private Long id;
    private String cmdbId;
    private String uarId;
    private Boolean deleteFlag;
    private String leitSystemId;
    private String leitSystemName;
    private String sequenceNumber;
    private String itCodeOfUser;
    private String email;
    private String userName;
    private String userRealName;
    private String country;
    private String department;
    private String appName;
    private String userId;
    private LocalDate userIdValidTo;
    private String authorizationTechnicalName;
    private String authorizationDescription;
    private LocalDate authorizationValidTo;
    private String accessLabel;
    private String firstLineManager;
    private String secondLineManager;
    private String itCodeOfBpo;
    private String bpoReviewDecision;
    private String userIdLock;
    private String accessReviewDecision;
    private String auditTrail;
    private String systemRole;
    private String roleDescription;
    private String bpoReviewStatus;
    private String lineManagerReviewStatus;
    private String lineManagersReviewDecision;
    private String finalReviewDecision;
    private LocalDate finalReviewTime;
    private String lineManager;
    private String  bpo;
    private String roleValidTo;
    private String overallSendStatus;
    private Integer currentRound;
    private String uuid;
    private String bpoEmail;
    /** NA policy CoC snapshot for the reviewed user: Y or N. */
    private String userCocType;
    /** NA policy CoC snapshot for a single BPO: Y or N. */
    private String bpoCocType;
    private String lineManagerEmail;
    private String appBpo;
    private String appBpoEmail;
    private String backupBpo;
    private String backupBpoEmail;

    /**lm决策的操作人*/
    private String lineManagerReviewItcode;
    /**lm决策的时间*/
    private LocalDate lineManagerReviewTime;
    /**bpo决策的操作人*/
    private String bpoReviewItcode;
    /**bpo决策的时间*/
    private LocalDate bpoReviewTime;
    /**修改bpo信息的操作人*/
    private String bpoUpdateBy;
    /**修改bpo信息的操作时间*/
    private LocalDateTime bpoUpdateTime;

    /** TIP: 用于标识对应的 BPO 人员是否属于以下管理层级：CEO, EVP, SVP, VP, ED
     若人员属于上述任一层级，则标记为 1；否则标记为 0
     若存在多人，则使用分号（; ）分隔
      */
    private String lineManagerBandEdFlag;
    private String bpoBandEdFlag;
    private String  backupBpoBandEdFlag;
    private String  appBpoBandEdFlag;
    private String  lineManagerLevelCode;
    /** 部门  */
    private String dept;

    /**LM职级
     * 传入code：1234
     * 响应orig：ED/VP/SVP/EVP/CEO
     * */
    @TableField(exist = false)
    private String lineManagerLevel;

}
