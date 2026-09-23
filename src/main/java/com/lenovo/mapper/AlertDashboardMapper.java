package com.lenovo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.AdvancedSearchBean;
import com.lenovo.bean.UarAlertBean;
import com.lenovo.bean.UarAlertBpoBean;
import com.lenovo.dto.MarkBpoInvalidRequest;
import com.lenovo.dto.UarAlertRequest;
import com.lenovo.entity.ItsApplicationData;
import com.lenovo.entity.UserAccessReviewAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AlertDashboardMapper extends BaseMapper<UserAccessReviewAlert> {

    /**
     * 检测任务
     * @param operator      操作人
     * @param alertType     警报类型
     * @see com.lenovo.entity.UserAccessReviewAlert alertType
     * @return
     */
    int insertUarAlertData(
            @Param("operator") String operator,
            @Param("alertType") Integer alertType
    );

    int deleteSourceData();

    Page<UarAlertBean> getList(
            Page<UarAlertBean> page,
            @Param("request") UarAlertRequest uarAlertRequest
    );

    long getListCount(@Param("request") UarAlertRequest uarAlertRequest);

    int restoreDataAndUpdateReview();

    int restoreDataAndUpdateLineManager();

    int insertManualBpoInvalid(
            @Param("operator") String operator,
            @Param("dto") MarkBpoInvalidRequest dto
    );

    long countInvalidBpo();

    /**
     * 处理BPO失效
     * @return
     */
    int restoreDataAndUpdateBPO();


    //处理BPO_ITCode失效----获取需要发送itCode失效邮件的信息
    List<UarAlertBean> getNeedDataByAlert(@Param("bean") UarAlertRequest uarAlertRequest);
    //处理BPO_ITCode失效----更新alert表的状态
    @Update("UPDATE user_access_review_alert SET alert_handle_status = 'Send',update_by = #{bean.updateBy} ,update_time = #{bean.updateTime}  WHERE alert_type = #{bean.alertType} AND alert_handle_status in ('Pending', 'Send')")
    void updateByAlertType(@Param("bean")UarAlertRequest uarAlertRequest);

    @Select("select email from ad_tb_upp_nature_2 where user_name = #{username}")
    List<String> getEmailByItCode(@Param("username") String username);






    //****************************************************************************************************************************************************************//
    //处理BPO_ITCode失效(不再使用)
    @Select("SELECT * FROM its_application_data WHERE cmdb_id IN (SELECT cmdb_id FROM user_access_review_alert WHERE alert_type = #{bean.alertType} GROUP BY cmdb_id)")
    List<ItsApplicationData> getNeedSendEmailByExpiredBpo(@Param("bean") UarAlertRequest uarAlertRequest);
    //不再使用
    @Select("SELECT bpo FROM user_access_review_alert WHERE alert_type = #{bean.alertType} GROUP BY bpo")
    List<String> getBpoItCode(@Param("bean")UarAlertRequest uarAlertRequest);
    //不再使用
    @Select("SELECT COUNT(1) FROM (SELECT cmdb_id FROM user_access_review_alert WHERE alert_type = #{bean.alertType} GROUP BY cmdb_id) AS sub_query")
    Long getAppCount(@Param("bean")UarAlertRequest uarAlertRequest);
    //不再使用
    @Select("SELECT count(1) FROM user_access_review_alert WHERE alert_type = #{bean.alertType}")
    Long getAccessCount(@Param("bean")UarAlertRequest uarAlertRequest);
    //****************************************************************************************************************************************************************//

    List<UarAlertBean> getAlertsForExport(@Param("request") UarAlertRequest uarAlertRequest);

    Page<UarAlertBpoBean> getAlertBpoSummary(Page<UarAlertBpoBean> page, @Param("request") UarAlertRequest uarAlertRequest);

    List<UarAlertBean> getAllAlertBpoSummary(@Param("request") UarAlertRequest uarAlertRequest);

    List<AdvancedSearchBean> getAlertInvalidBpoList(@Param("request") UarAlertRequest uarAlertRequest);
}
