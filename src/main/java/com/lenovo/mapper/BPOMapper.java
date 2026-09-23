package com.lenovo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.AdvancedSearchBean;
import com.lenovo.bean.BpoInfoBean;
import com.lenovo.bean.RiskManagementStatisticsBean;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.entity.BatchOperationRequest;
import com.lenovo.entity.UserAccessReview;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
* @author mercury
* @description 针对表【risk_management】的数据库操作Mapper
* @createDate 2024-10-28 11:38:48
* @Entity com.lenovo.entity.RiskManagement
*/
public interface BPOMapper extends BaseMapper<UserAccessReview> {


    RiskManagementStatisticsBean BPOStatistics(@Param("dataRangeMap") Map<String, List<String>> dataRangeMap);
    Page<UserAccessReview> query(Page p, @Param("UseAccessReviewBean") UseAccessReviewBean useAccessReviewBean);

    int updateBpoAccessReviewStatus(
            @Param("request") BatchOperationRequest batchOperationRequest,
            @Param("bpoReviewItcode") String lineManagerReviewItcode,
            @Param("completedCmdbIds") List<String> completedCmdbIds
    );

    List<UserAccessReview> getAllBPOReview(@Param("UseAccessReviewBean") UseAccessReviewBean useAccessReviewBean,
                                           @Param("language") String language);
    void batchBPOUpdate(
            @Param("list") List<UserAccessReview> reviews,
            @Param("currentUserId") String currentUserId
    );

    List<UserAccessReview> getBPOReviewByLineManager(@Param("UseAccessReviewBean") UseAccessReviewBean useAccessReviewBean);
    List<UserAccessReview> getBPOReviewByBpo(@Param("UseAccessReviewBean") UseAccessReviewBean useAccessReviewBean);

    UserAccessReview findByUniqueKeys(
            @Param("cmdbId") String cmdbId,
            @Param("itCode") String itCodeOfUser,
            @Param("userId") String userId,
            @Param("systemRole") String systemRole,
            @Param("roleDescription") String roleDescription,
            @Param("leitSystemId") String leitSystemId
    );
    List<AdvancedSearchBean>  getDistinctApplication(@Param("bpoReviewStatus") String bpoReviewStatus,@Param("itCodeOfUser") String itCodeOfUser,@Param("bpoReviewDecision") String bpoReviewDecision);

    int updateBpoInfo(@Param("info") BpoInfoBean bpoInfo);

    void batchUpdateBpoInfo(
            @Param("list") List<UserAccessReview> records,
            @Param("currentUserId") String currentUserId
    );

    UserAccessReview findBySequenceNumber(String sequenceNumber);

    List<String> getCompletedCmdbIdByConditions(@Param("request") BatchOperationRequest request);

    int checkReviewAbility(@Param("request") BatchOperationRequest batchOperationRequest);
}




