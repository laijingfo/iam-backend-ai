package com.lenovo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.AdvancedSearchBean;
import com.lenovo.bean.RiskManagementStatisticsBean;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.entity.UserAccessReview;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author mercury
 * @description 针对表【risk_management】的数据库操作Mapper
 * @createDate 2024-10-28 11:38:48
 * @Entity com.lenovo.entity.RiskManagement
 */
public interface MyAcessMapper extends BaseMapper<UserAccessReview> {


    RiskManagementStatisticsBean myAccessStatistics(String itCodeOfUser);

    Page<UserAccessReview> query(Page p, @Param("UseAccessReviewBean") UseAccessReviewBean useAccessReviewBean);

    void updateBPOReview(@Param("list") List<UserAccessReview> userAccessReview);

    void batchUpdate1(@Param("list") List<UserAccessReview> reviews);

    List<UserAccessReview> getAllLMyAccessReview(@Param("UseAccessReviewBean") UseAccessReviewBean useAccessReviewBean, @Param("language") String language);

    void batchBPOUpdate(@Param("list") List<UserAccessReview> reviews);

    UserAccessReview findByUniqueKeys(
            @Param("itCode") String itCodeOfUser,
            @Param("appName") String appName,
            @Param("systemRole") String systemRole);

    List<AdvancedSearchBean> getDistinctApplication(@Param("finalReviewStatus") String finalReviewStatus, @Param("itCodeOfUser") String itCodeOfUser, @Param("finalReviewDecision") String finalReviewDecision);

}




