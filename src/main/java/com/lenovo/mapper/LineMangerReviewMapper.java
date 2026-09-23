package com.lenovo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.AdvancedSearchBean;
import com.lenovo.bean.RiskManagementStatisticsBean;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.entity.BatchOperationRequest;
import com.lenovo.entity.UserAccessReview;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* @author mercury
* @description 针对表【risk_management】的数据库操作Mapper
* @createDate 2024-10-28 11:38:48
* @Entity com.lenovo.entity.RiskManagement
*/
public interface LineMangerReviewMapper extends BaseMapper<UserAccessReview> {



    Page<UserAccessReview> query(Page p, @Param("UseAccessReviewBean") UseAccessReviewBean useAccessReviewBean);

    List<UserAccessReview> getAllLineManagerReview(@Param("UseAccessReviewBean") UseAccessReviewBean useAccessReviewBean,
                                                   @Param("language") String language);

    int updateLmAccessReviewStatus(
            @Param("request") BatchOperationRequest batchOperationRequest,
            @Param("lineManagerReviewItcode") String lineManagerReviewItcode,
            @Param("completedCmdbIds") List<String> completedCmdbIds
    );

    RiskManagementStatisticsBean UseAccessReviewStatistics(List<String> dataRange);

    UserAccessReview findByUniqueKeys(
            @Param("cmdbId") String cmdbId,
            @Param("itCode") String itCodeOfUser,
            @Param("userId") String userId,
            @Param("systemRole") String systemRole,
            @Param("roleDescription") String roleDescription,
            @Param("leitSystemId") String leitSystemId
        );

    // 批量更新记录
    void batchUpdate(
            @Param("list") List<UserAccessReview> reviews,
            @Param("lineManagerReviewItcode") String lineManagerReviewItcode
    );

    //uar的Application的下拉选

    List<AdvancedSearchBean> getDistinctApplication( @Param("lineManagerReviewStatus")String lineManagerReviewStatus,@Param("itCodeOfUser")String itCodeOfUser,@Param("lineManagersReviewDecision") String lineManagersReviewDecision);


    UserAccessReview findBySequenceNumber(String sequenceNumber);

    List<String> getCompletedCmdbIdByConditions(@Param("request") BatchOperationRequest request);

    int checkReviewAbility(@Param("request") BatchOperationRequest request);
}




