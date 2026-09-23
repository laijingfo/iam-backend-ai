package com.lenovo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.UarCycleMaintenanceBean;
import com.lenovo.bean.UarCycleBean;
import com.lenovo.entity.UarCycleMaintenance;
import com.lenovo.bean.ApplicationCycleInfoBean;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * @author mercury
 * @description 针对表【risk_management】的数据库操作Mapper
 * @createDate 2024-10-28 11:38:48
 * @Entity com.lenovo.entity.RiskManagement
 */
public interface UarCycleMaintenanceMapper extends BaseMapper<UarCycleMaintenance> {


    Page<ApplicationCycleInfoBean> query(Page p, @Param("uarCycleMaintenanceBean") UarCycleMaintenanceBean uarCycleMaintenanceBean);

    List<ApplicationCycleInfoBean> queryFullData( @Param("uarCycleMaintenanceBean") UarCycleMaintenanceBean uarCycleMaintenanceBean);

    List<Map<String, Object>> uar_cycle_maintenance_distinct_list();

    int batchUpdateUar_cycle_maintenance_distinctStatus(@Param("list") List<UarCycleMaintenanceBean> updateList);

    List<Map<String, Object>> uar_cycle_maintenance_distinct_application();

    List<String> queryIdListByFilters(
            @Param("uarCycleMaintenanceBean") UarCycleMaintenanceBean filters
    );

    String queryUarIdByCmdbId(String cmdbId);

    UarCycleMaintenance queryLatestByCmdbId(String cmdbId);

    int batchCompleteCycle(List<String> cmdbId);

    List<String> queryCompletedCmdbIdByUarId(List<String> distinctCmdbIdList);

    boolean queryUarCompletedCycleByCmdbId(String cmdbId);
}




