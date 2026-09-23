package com.lenovo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.entity.UserAccessReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LineMangerBandReviewMapper extends BaseMapper<UserAccessReview> {
    List<UserAccessReview> getAllLineManagerBandReview(@Param("UseAccessReviewBean") UseAccessReviewBean useAccessReviewBean, String language);

    UserAccessReview findByUniqueKeys(
            @Param("cmdbId") String cmdbId,
            @Param("itCode") String itCodeOfUser,
            @Param("userId") String userId,
            @Param("systemRole") String systemRole,
            @Param("roleDescription") String roleDescription,
            @Param("leitSystemId") String leitSystemId
    );

    void batchUpdate(
            @Param("list") List<UserAccessReview> reviews,
            @Param("lineManagerReviewItcode") String lineManagerReviewItcode
    );

    UserAccessReview findBySequenceNumber(String sequenceNumber);
}
