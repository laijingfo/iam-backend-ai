package com.lenovo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * @Description TODO 归档数据Mapper类
 * @ClassName ArchiveMapper
 * @Author wangfenglong
 * @Date 2026/3/30 16:03
 **/
@Mapper
public interface ArchiveMapper
{
    int insertMaintenanceBySelect(@Param("maintenanceId") String maintenanceId, @Param("archiver") String archiver);
    @Select("SELECT COUNT(*) FROM uar_cycle_maintenance")
    Long selectMaintenanceCount();
    @Select("SELECT COUNT(*) FROM uar_cycle_maintenance_history WHERE maintenance_id = #{maintenanceId}")
    Long selectMaintenanceHistoryCount(@Param("maintenanceId") String maintenanceId);

    int insertReviewBySelect(@Param("maintenanceId") String maintenanceId, @Param("archiver") String archiver);
    @Select("SELECT COUNT(*) FROM user_access_review")
    Long selectReviewCount();
    @Select("SELECT COUNT(*) FROM user_access_review_history WHERE maintenance_id = #{maintenanceId}")
    Long selectReviewHistoryCount(@Param("maintenanceId") String maintenanceId);

    int insertAlertBySelect(@Param("maintenanceId") String maintenanceId, @Param("archiver") String archiver);
    @Select("SELECT COUNT(*) FROM user_access_review_alert")
    Long selectAlertCount();
    @Select("SELECT COUNT(*) FROM user_access_review_alert_history WHERE maintenance_id = #{maintenanceId}")
    Long selectAlertHistoryCount(@Param("maintenanceId") String maintenanceId);

    @Update("TRUNCATE TABLE uar_cycle_maintenance,user_access_review,user_access_review_alert")
    void truncateTable();
}
