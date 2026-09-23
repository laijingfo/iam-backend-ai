package com.lenovo.bean;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;

import com.baomidou.mybatisplus.annotation.TableField;
import java.math.BigDecimal;
import java.util.Map;

/**
 * @author: liufz
 * @date: 2023-05-05
 * @Description: 这个实体有个2个类型（图表，列表）；3个模块（审核完成，审核回复，审核结果）
 */
public class DashboardChartReviewBean {

    /**
     * 统计
     */
    @Data
    public static class OverallStatistics{
        private Long total;
        private Long reviewed;
        private String completionRate;
        private String lineManagerResponseRate;
        private String bpoResponseRate;


        public void setCompletionRate(String completionRate) {
            this.completionRate = completionRate + "%";
        }
        public void setLineManagerResponseRate(String lineManagerResponseRate) {
            this.lineManagerResponseRate = lineManagerResponseRate + "%";
        }
        public void setBpoResponseRate(String bpoResponseRate) {
            this.bpoResponseRate = bpoResponseRate + "%";
        }
    }

    @Data
    public static class LmLevelStatistics{
        private String level;           // Below / ED / VP / Above
        private Long totalPerson;       // 该level下的总人数
        private Long totalItems;        // 该level下的总记录数（Review total）
        private Long completedPerson;   // 完成人数（要审核的数据 全部完成）
        private Long uncompletedPerson; // 未完成人数（要审核的数据 存在未完成）


    }


    @Data
    public static class OverallList{
        private String cmdbId;
        private String applicationName;
        private String appOperationOwner;
        private String appOperationFocal;
        private String operationOwnerDomain;
        private String operationOwnerTower;
        private Long total;
        private Long reviewed;
        private Long pendingReview;
        private String completionRate;
        private String lineManagerResponseRate;
        private String bpoResponseRate;
        private Long keep;
        private String keepRate;
        private Long remove;
        private String removeRate;


        public void setCompletionRate(String completionRate) {
            this.completionRate = completionRate + "%";
        }
        public void setLineManagerResponseRate(String lineManagerResponseRate) {
            this.lineManagerResponseRate = lineManagerResponseRate + "%";
        }
        public void setBpoResponseRate(String bpoResponseRate) {
            this.bpoResponseRate = bpoResponseRate + "%";
        }
        public void setKeepRate(String keepRate) {
            this.keepRate = keepRate + "%";
        }
        public void setRemoveRate(String removeRate) {
            this.removeRate = removeRate + "%";
        }
    }


}
