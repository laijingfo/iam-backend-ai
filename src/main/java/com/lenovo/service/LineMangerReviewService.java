package com.lenovo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lenovo.bean.AdvancedSearchBean;
import com.lenovo.bean.ImportResult;
import com.lenovo.bean.RiskManagementStatisticsBean;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.entity.BatchOperationRequest;
import com.lenovo.entity.UserAccessReview;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.io.IOException;

public interface LineMangerReviewService extends IService<UserAccessReview> {

    public Page<UserAccessReview> query(UseAccessReviewBean useAccessReviewBean, Integer page, Integer size);

    List<UserAccessReview> getAllLineManagerReview(UseAccessReviewBean useAccessReviewBean, String language);

    void exportExcel(HttpServletResponse response, UseAccessReviewBean query, String language) throws IOException;

    Map<String, Object> updateLmAccessReviewStatus(BatchOperationRequest batchOperationRequest);

    RiskManagementStatisticsBean UseAccessReviewStatistics(List<String> dataRange);

    ImportResult importExcel(MultipartFile file, List<String> dataRange) throws Exception;

    List<AdvancedSearchBean>  getDistinctApplication(String lineManagerReviewStatus,String itCodeOfUser,String lineManagersReviewDecision);

    void deleteRelatedCaches();
}
