package com.lenovo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lenovo.bean.*;
import com.lenovo.entity.BatchOperationRequest;
import com.lenovo.entity.UserAccessReview;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface BPOService extends IService<UserAccessReview> {

    RiskManagementStatisticsBean BPOStatistics(Map<String, List<String>> dataRangeMap);

    public Page<UserAccessReview> query(UseAccessReviewBean useAccessReviewBean, Integer page, Integer size);

    Map<String, Object> updateBpoAccessReviewStatus(BatchOperationRequest batchOperationRequest);

    List<UserAccessReview> getAllLBPOReview(UseAccessReviewBean useAccessReviewBean, String language);

    void exportExcel(HttpServletResponse response, UseAccessReviewBean query, String language) throws IOException;

    ImportResult importBPOExcel(MultipartFile file, Map<String, List<String>> dataRangeMap) throws IOException;

    List<AdvancedSearchBean> getDistinctApplication(String bpoReviewStatus, String itCodeOfUser, String bpoReviewDecision);

    Boolean updateBpoInfo(BpoInfoBean bpoInfo);

    ImportResult importBPOInfoExcel(MultipartFile file) throws IOException;
}
