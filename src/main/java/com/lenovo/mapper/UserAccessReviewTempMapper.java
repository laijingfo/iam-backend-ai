package com.lenovo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.AdvancedSearchBean;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.entity.UserAccessReview;
import com.lenovo.entity.UserAccessReviewTemp;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

public interface UserAccessReviewTempMapper extends BaseMapper<UserAccessReviewTemp>
{
    @Select("SELECT sub.label AS label,sub.label AS value FROM (SELECT DISTINCT t.bpo AS label FROM user_access_review_temp t WHERE t.bpo IS NOT NULL AND t.bpo != '' ORDER BY label) AS sub")
    List<AdvancedSearchBean> selectDistinctBpo();

    //待发送页面:发送邮件之前统计lm和bpo的数量:邮件数量(lm+bpo)(全选)
    Map<String, Object> getBpoAndLineManagerCountByDistinct(@Param("bean") UseAccessReviewBean bean, @Param("roleFlag") String roleFlag);
    int getBpoCountDistinctBySequenceNumbers(@Param("sequenceNumbers") List<String> sequenceNumbers);
    int getLineManagerCountDistinctBySequenceNumbers(@Param("sequenceNumbers") List<String> sequenceNumbers);

    //发送失败页面统计待发送邮件数量:发送邮件之前统计lm和bpo的数量:邮件数量(lm+bpo)(全选)
    Map<String, Object> getBpoAndLineManagerCountByDistinctForFailedSend(@Param("bean") UseAccessReviewBean bean, @Param("roleFlag") String roleFlag);
    int getBpoCountDistinctBySequenceNumbersForFailedSendEmail(@Param("sequenceNumbers") List<String> sequenceNumbers);
    int getLineManagerCountDistinctBySequenceNumbersForFailedSendEmail(@Param("sequenceNumbers") List<String> sequenceNumbers);

    int getSendEmailTotalCount(@Param("roleFlag")String roleFlag,@Param("overallSendStatus")String overallSendStatus,@Param("bean") UseAccessReviewBean bean);

    // 根据sequenceNumbers查询记录（不包含邮件状态）
    List<UserAccessReview> selectBySequenceNumbers(@Param("sequenceNumbers") List<String> sequenceNumbers);
    int updateCurrentRoundBatch(@Param("sequenceNumbers") List<String> sequenceNumbers);

}