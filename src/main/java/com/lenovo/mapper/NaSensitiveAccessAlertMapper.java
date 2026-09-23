package com.lenovo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.NaSensitiveAccessAlertBean;
import com.lenovo.dto.NaSensitiveAccessAlertRequest;
import com.lenovo.entity.NaSensitiveAccessAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NaSensitiveAccessAlertMapper extends BaseMapper<NaSensitiveAccessAlert> {
    int insertNewAlerts(@Param("operator") String operator);

    int resolveMissingAlerts(@Param("operator") String operator);

    Page<NaSensitiveAccessAlertBean> selectAlertPage(
            Page<NaSensitiveAccessAlertBean> page,
            @Param("request") NaSensitiveAccessAlertRequest request
    );

    long selectAlertCount(@Param("request") NaSensitiveAccessAlertRequest request);

    List<NaSensitiveAccessAlertBean> selectAlertsForExport(
            @Param("request") NaSensitiveAccessAlertRequest request
    );

    int suppressAlert(
            @Param("id") Long id,
            @Param("suppressedReason") String suppressedReason,
            @Param("suppressedRequestedBy") String suppressedRequestedBy,
            @Param("operator") String operator,
            @Param("dataRange") List<String> dataRange
    );

    List<NaSensitiveAccessAlert> selectPendingAlertsToSend();

    int updateLastSendTime(
            @Param("id") Long id,
            @Param("sendTime") LocalDateTime sendTime,
            @Param("operator") String operator
    );
}
