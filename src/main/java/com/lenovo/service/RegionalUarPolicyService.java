package com.lenovo.service;

import com.lenovo.bean.UseAccessReviewBean;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Entry point for UAR business-policy differences between regions. */
public interface RegionalUarPolicyService {

    /** Export line manager review. */
    void exportLineManagerReview(
            HttpServletResponse response,
            UseAccessReviewBean query,
            String language
    ) throws IOException;

    /** Export BPO review. */
    void exportBpoReview(
            HttpServletResponse response,
            UseAccessReviewBean query,
            String language
    ) throws IOException;

    /** Format access label for export. */
    String formatAccessLabelForExport(String accessLabel, String language);

}
