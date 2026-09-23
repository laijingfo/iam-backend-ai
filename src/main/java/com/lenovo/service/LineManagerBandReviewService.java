package com.lenovo.service;

import com.lenovo.bean.ImportResult;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.entity.UserAccessReview;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface LineManagerBandReviewService {
    List<UserAccessReview> getAllLineManagerBandReview(UseAccessReviewBean useAccessReviewBean, String language);
    ImportResult importExcel(MultipartFile file) throws Exception;

}
