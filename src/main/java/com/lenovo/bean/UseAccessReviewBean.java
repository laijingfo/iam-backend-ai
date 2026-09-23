package com.lenovo.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import com.baomidou.mybatisplus.annotation.TableField;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;


@Data
public class UseAccessReviewBean {

    private String sequenceNumber;
    private String itCodeOfUser;
    private String email;
    private String userName;
    private String country;
    private String department;
    private String cmdbId;
    private String appName;
    private String userId;
    private LocalDate userIdValidTo;
    private String authorizationTechnicalName;
    private String authorizationDescription;
    private LocalDate authorizationValidTo;
    private String accessLabel;
    private String lineManager;
    private String secondLineManager;
    private String bpo;
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
    private String overallSendStatus;
    private String dataOfRole; //根据角色隔离数据
    private List<String> dataRange;
    private Map<String, List<String>> dataRangeMap;

    private List<String> cmdbIdList;//从redis中获取的UAR_Processer权限的cmdbIdOfUarProcesser的cmdbID
    private String appOperationOwner;
    private String appOperationFocal;
    private String operationOwnerDomain;
    private String operationOwnerTower;

    private String uarProcessor;

    /**lm决策的操作人*/
    private String lineManagerReviewItcode;
    /**lm决策的时间*/
    private String lineManagerReviewTime;
    /**bpo决策的操作人*/
    private String bpoReviewItcode;
    /**bpo决策的时间*/
    private String bpoReviewTime;

    /**LM职级
     * 传入code：1234
     */
    private String lineManagerLevelCode;

    /** LM部门  */
    private String dept;

    /** 排序字段  */
    private String sortField;
    /** 排序方式  */
    private String sortOrder;
    //UAR发送多选的应用编号
    private List<String> cmdbIdListOfChoose;
    private List<String> appNameListOfChoose;


    /**
     * 查询的数据周期
     * */
    private String currentDataCycle;

    /**
     * 是否是当前周期
     * 0:不是 1:是
     * */
    private Integer isCurrentCycle;

    /**
     * 数据周期类型(来源UAR LR)
    **/
    private String cycleType;

    /** 异常工单号 */
    private String exceptionTicketNo;

    /** 异常处理结果 */
    private String exceptionResultDecision;

    /** 异常处理原因 */
    private String exceptionReason;

    /** 异常处理人 */
    private String exceptionAdjustedBy;

    /** 异常处理时间 */
    private LocalDate exceptionAdjustedTime;

    /** 用户的CoC类型 */
    private String userCocType;

    /** BPO的CoC类型 */
    private String bpoCocType;

}
