package com.lenovo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lenovo.bean.AdvancedSearchBean;
import com.lenovo.bean.ImportResult;
import com.lenovo.bean.RiskManagementStatisticsBean;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.entity.UserAccessReview;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface MyAccessService extends IService<UserAccessReview> {

    RiskManagementStatisticsBean myAccessStatistics(String itCodeOfUser);

    public Page<UserAccessReview> query(UseAccessReviewBean useAccessReviewBean, Integer page, Integer size);

    public void updateBPOReview(List<UserAccessReview> userAccessReview);

    void batchUpdate1(List<UserAccessReview> userAccessReview);

    List<UserAccessReview> getAllLMyAccessReview(UseAccessReviewBean useAccessReviewBean, String language);

    ImportResult importBPOExcel(MultipartFile file) throws IOException;

    List<AdvancedSearchBean> getDistinctApplication(String finalReviewStatus, String itCodeOfUser, String finalReviewDecision);

}
