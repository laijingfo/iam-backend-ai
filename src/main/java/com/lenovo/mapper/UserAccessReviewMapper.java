package com.lenovo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.AdvancedSearchBean;
import com.lenovo.bean.EmailFailedBean;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.constant.AccessReviewScope;
import com.lenovo.dto.BatchSendRequest;
import com.lenovo.entity.*;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface UserAccessReviewMapper extends BaseMapper<UserAccessReview> {
    // 新增方法：批量更新邮件发送状态
    int updateOverallSendStatusBatch(@Param("sequenceNumbers") List<String> sequenceNumbers,
                                     @Param("status") String status);

    //根据发送状态分页查询(不关联映射集合)
    Page<UserAccessReview> selectBySendStatusToRewrite(Page<UserAccessReview> page, @Param("overallSendStatus") String overallSendStatus, @Param("bean") UseAccessReviewBean bean,@Param("roleFlag") String roleFlag);

    //发送通知:更新待发送列表
    void updateWaitPendingList(@Param("bean") UseAccessReviewBean bean,@Param("roleFlag") String roleFlag);

    // 新增方法：查询历史记录（overall_send_status不为Pending）
    Page<UserAccessReview> selectHistoryRecords(Page<UserAccessReview> page, @Param("useAccessReviewBean") UseAccessReviewBean useAccessReviewBean);

    List<AdvancedSearchBean> selectDistinctApplicationBySendStatus(@Param("roleFlag") String roleFlag,@Param("overallSendStatus") String overallSendStatus,@Param("bean") UseAccessReviewBean bean);

    List<UserAccessReview> selectAllHistoryRecords(@Param("useAccessReviewBean") UseAccessReviewBean useAccessReviewBean, @Param("language") String language);

    void localMergeUarDoUpdateByCmdbId(
            @Param("cmdbId") String cmdbId,
            @Param("uarId") String uarId,
            @Param("accessReviewScope") AccessReviewScope accessReviewScope
    );

    int batchInitUarCycleUserAccessReview(
            @Param("list") List<UarCycleMaintenance> uarCycleMaintenanceBeanList
        );

    /**
     * @Title 批量更新It code无效的审核结果
     * @Desc
     **/
    int batchUpdateUserInvalidReviewResult(@Param("cmdbId") List<String> cmdbId);


    int batchUpdateLmSVPAccessReviewResult(@Param("cmdbId") List<String> cmdbId);
    /**
     * @Title 批量更新最终审核结果
     * @Desc
     **/
    int batchUpdateFinalReviewDecision(@Param("cmdbId") List<String> cmdbId);

    /**
     * @Description TODO 发送邮件获取Uar周期的结束时间
     * @author wangfenglong
     * @date 2025/12/9 17:39
    **/
    String getuarCycleEndDateByuarId(@Param("uarId")String uarId);

    void truncateByCmdbId(String cmdbId);

    int batchSave( @Param("list") List<UserAccessReview> validRecords,
                    @Param("operator") String operator
    );


    /**
     * 批量插入 allowSendEmail 核心方法【你的代码直接调用这个】
     * @param list 待发送邮件集合 allowSendEmail
     * @return 插入成功的条数
     */
    int batchInsert(@Param("list") List<UserAccessReview> list);
    void truncateTempAllowSendTable();



    int batchInsertForLm(@Param("list") List<UserAccessReview> list);
    @Update("TRUNCATE TABLE allow_send_email_for_lm")
    void truncateAllowSendEmailForLm();
    @Select("select * from allow_send_email_for_lm")
    List<AllowSendEmailForLm> getAllowSendEmailForLm();

    int batchInsertForBpo(@Param("list") List<UserAccessReview> list);
    @Update("TRUNCATE TABLE allow_send_email_for_bpo")
    void truncateAllowSendEmailForBpo();
    @Select("select * from allow_send_email_for_bpo")
    List<AllowSendEmailForBpo> getAllowSendEmailForBpo();


    int batchInsertForLmFailed(@Param("list") List<UserAccessReview> list);
    @Update("TRUNCATE TABLE allow_send_email_for_lm_failed")
    void truncateAllowSendEmailForLmFailed();
    @Update("TRUNCATE TABLE allow_send_email_for_bpo_failed")
    void truncateAllowSendEmailForBpoFailed();
    int batchInsertForBpoFailed(@Param("list") List<UserAccessReview> list);
    @Select("SELECT DISTINCT ON(recipient_emails) recipient_emails,recipient_it_code from uar_mail_send_log where 1=1 and delegate = 'delegatee' and status = 'FAILED' ORDER BY recipient_emails, send_time DESC")
    List<UarMailSendLog> selectDelagateBpo();
    List<UarMailSendLog> selectDelagateBpoByrandomSequence(@Param("sequenceNumbers") List<String> sequenceNumbers);
    @Select("select * from allow_send_email_for_bpo_failed")
    List<AllowSendEmailForBpo> getAllowSendEmailForBpoFailed();
    @Select("select * from allow_send_email_for_lm_failed")
    List<AllowSendEmailForLm> getAllowSendEmailForLmFailed();


    List<UserAccessReview> getAllNeeDToSendEmailFromUserAccessReviewNew(@Param("bean") UseAccessReviewBean bean,@Param("roleFlag") String roleFlag,@Param("successFailedFlag") String successFailedFlag);
    List<UserAccessReview> getAllNeeDToSendEmailFromUserAccessReviewBySequenceNumbers(@Param("sequenceNumbers") List<String> sequenceNumbers);


    List<UserAccessReview> getLmFromUserAccessReviewNew(@Param("bean") UseAccessReviewBean bean,@Param("roleFlag") String roleFlag,@Param("successFailedFlag") String successFailedFlag);
    List<UserAccessReview> getBpoFromUserAccessReviewNew(@Param("bean") UseAccessReviewBean bean,@Param("roleFlag") String roleFlag,@Param("successFailedFlag") String successFailedFlag);



    List<String> selectDistinctSystemRolesByCmdbId(@Param("cmdbId") String cmdbId, @Param("bpo") String bpo);

    //发送邮件统计各种发送状态的邮件数量
    int getSendEmailTotalCount(@Param("roleFlag")String roleFlag,@Param("overallSendStatus")String overallSendStatus,@Param("bean") UseAccessReviewBean bean);

    //用户发邮件--查询final_review_decision最终审核是空的用户数据
    List<UserAccessReview> getItCodeUserByFinalReviewDecisionIsEmpty(@Param("useAccessReviewBean") UseAccessReviewBean useAccessReviewBean,@Param("roleFlag") String roleFlag);

    //用户发邮件:查询lm或者bpo的审核结果是remove的用户数据
    List<UserAccessReview> selectUserByReviewResultIsRemove(@Param("useAccessReviewBean") UseAccessReviewBean useAccessReviewBean,@Param("roleFlag") String roleFlag);

    //用户发邮件:将lm或者bpo审核结果是remove的用户数据写入到表中
    int batchInsertReviewRemoveUser(@Param("list") List<String> list);

    @Update("TRUNCATE TABLE allow_send_email_for_result_remove_user")
    void truncateTableAllowSendEmailForResultRemoveUser();

    //用户发邮件--查询用户表获取的是blow以下的用户
    List<String> getUserByBlow(@Param("flag")String flag);


    @Select("SELECT email FROM ad_tb_upp_nature_2 WHERE lower(user_name) = #{userName}")
    List<String> getUserEmailByUserName(@Param("userName")String userName);

    List<String> getCcEmailByLineManager(@Param("lineManager")String lineManager);

    //给Lm发邮件--查询lineManager的cc邮件
    @MapKey("line_manager")
    Map<String, Map<String,String>> getCcEmailByLineManagerNew(@Param("allowSendEmail")List<UserAccessReview> allowSendEmail, @Param("bean") UseAccessReviewBean bean);

    //用户发邮件--清空该表
    @Update("TRUNCATE TABLE allow_send_email_include_user")
    void truncateTempAllowUserSendTable();

    //用户发邮件--插入到用户邮件发送表给领导看
    int batchInsertAllowUser(@Param("list") List<String> list);

    //用户发邮件--查询出用户数据
    @Select("SELECT it_code_of_user FROM allow_send_email_include_user")
    List<String> getUserEmailFromAllowSendEmailInclude(@Param("useAccessReviewBean")UseAccessReviewBean useAccessReviewBean);

    //发送【最终审核是空的用户】邮件：查询出allow_send_email_for_result_remove_user预发送数据
    @Select("SELECT it_code_of_user_email FROM allow_send_email_for_result_remove_user")
    List<String> getUserEmailFromAllowSendEmailForResultRemoveUser(@Param("useAccessReviewBean")UseAccessReviewBean useAccessReviewBean);

    //用户发邮件,按照需求查询出【最终审核是移除的用户】的userIdLock是0的才可以发送
    List<AllowSendEmailIncludeFinalRemoveUser> getUserEmailFromAllowSendEmailIncludeFinalRemove(@Param("limitSize") Integer limitSize, @Param("useAccessReviewBean") UseAccessReviewBean useAccessReviewBean);

    //元首类使用的


    // 根据sequenceNumber查询数据
    @Select("SELECT * FROM user_access_review WHERE sequence_number = #{sequenceNumber}")
    UserAccessReview findBySequenceNumber(String sequenceNumber);

    // 用户发邮件--获取最终审核是移除的用户数据
    List<UserAccessReview> getFinalRemoveUser(@Param("useAccessReviewBean") UseAccessReviewBean useAccessReviewBean,@Param("roleFlag") String roleFlag);

    @Update("TRUNCATE TABLE allow_send_email_include_final_remove_user")
    void truncateTempAllowUserSendTableForFinalRemove();
    // 用户发邮件--将最终审核是移除的用户写入到表中
    int batchInsertAllowUserIncludeFinalRemove(@Param("list") List<UserAccessReview> list);

    Integer batchUpdate(@Param("records") List<UserAccessReview> validRecords);

    List<UserAccessReview> getDistinctUserGroup(@Param("userItCode") String userItCode);

    Integer cleanFinalRemoveUserSendTable();

    Integer relatedLeaderAndSave(@Param("employees") List<String> employees);

    List<String> findFinalRemoveUser();

    int updateDelagateBpoSequenceNum(@Param("delegateBpoList") List<String> delegateBpoList);

    int batchUpdateExceptionProcess(@Param("list") List<UserAccessReview> batch, @Param("operator") String operator);
}
